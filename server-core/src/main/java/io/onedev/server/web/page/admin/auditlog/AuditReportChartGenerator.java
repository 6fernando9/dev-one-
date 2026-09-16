package io.onedev.server.web.page.admin.auditlog;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import io.onedev.server.model.AuditEvent;
import io.onedev.server.model.support.AuditEventType;

public class AuditReportChartGenerator {

	private static final Color[] PALETTE = {
		new Color(74, 144, 217),
		new Color(230, 126, 34),
		new Color(46, 204, 113),
		new Color(231, 76, 60),
		new Color(155, 89, 182),
		new Color(52, 73, 94),
		new Color(241, 196, 15),
		new Color(26, 188, 156),
		new Color(233, 30, 99),
		new Color(0, 150, 136)
	};

	public static String generateBarChartBase64(List<AuditEvent> events, Date filterFrom, Date filterTo) {
		if (events.isEmpty())
			return "";

		var zone = ZoneId.systemDefault();
		var from = filterFrom.toInstant().atZone(zone).toLocalDate();
		var to = filterTo.toInstant().atZone(zone).toLocalDate();

		var countsByDay = new LinkedHashMap<LocalDate, Long>();
		for (var d = from; !d.isAfter(to); d = d.plusDays(1))
			countsByDay.put(d, 0L);

		for (var event : events) {
			var day = event.getDate().toInstant().atZone(zone).toLocalDate();
			countsByDay.merge(day, 1L, Long::sum);
		}

		int width = 700;
		int height = 280;
		int padding = 50;
		int bottomPadding = 60;
		int chartWidth = width - 2 * padding;
		int chartHeight = height - padding - bottomPadding;

		var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		var g = image.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);

		long maxCount = countsByDay.values().stream().mapToLong(Long::longValue).max().orElse(1);
		if (maxCount == 0) maxCount = 1;

		var days = new ArrayList<>(countsByDay.keySet());
		var counts = new ArrayList<>(countsByDay.values());
		int barWidth = Math.max(1, (chartWidth - (days.size() - 1) * 2) / days.size());

		g.setColor(new Color(220, 220, 220));
		for (int i = 0; i <= 4; i++) {
			int y = padding + (int) (chartHeight * (1 - i / 4.0));
			g.drawLine(padding, y, width - padding, y);
		}

		g.setColor(new Color(80, 80, 80));
		g.setFont(new Font("Helvetica", Font.PLAIN, 9));
		var fm = g.getFontMetrics();
		for (int i = 0; i <= 4; i++) {
			int y = padding + (int) (chartHeight * (1 - i / 4.0));
			String label = String.valueOf((int) (maxCount * i / 4.0));
			g.drawString(label, padding - fm.stringWidth(label) - 5, y + 3);
		}

		for (int i = 0; i < days.size(); i++) {
			int x = padding + i * (barWidth + 2);
			int barHeight = (int) (chartHeight * counts.get(i) / maxCount);
			int y = padding + chartHeight - barHeight;

			g.setColor(PALETTE[0]);
			g.fillRect(x, y, barWidth, barHeight);

			if (days.size() <= 14 || i % Math.max(1, days.size() / 10) == 0) {
				g.setColor(new Color(100, 100, 100));
				g.setFont(new Font("Helvetica", Font.PLAIN, 8));
				var labelFm = g.getFontMetrics();
				String label = days.get(i).getDayOfMonth() + "/" + (days.get(i).getMonthValue());
				int labelX = x + barWidth / 2 - labelFm.stringWidth(label) / 2;
				g.drawString(label, labelX, height - bottomPadding + 15);
			}
		}

		g.dispose();
		return toBase64(image);
	}

	public static String generatePieChartBase64(List<AuditEvent> events) {
		if (events.isEmpty())
			return "";

		var typeCounts = new LinkedHashMap<AuditEventType, Long>();
		for (var event : events)
			typeCounts.merge(event.getEventType(), 1L, Long::sum);

		int width = 500;
		int height = 300;
		int centerX = 200;
		int centerY = height / 2;
		int radius = 100;

		var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		var g = image.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);

		long total = typeCounts.values().stream().mapToLong(Long::longValue).sum();
		if (total == 0) total = 1;

		double startAngle = 90;
		int colorIndex = 0;
		for (var entry : typeCounts.entrySet()) {
			double sweepAngle = 360.0 * entry.getValue() / total;
			g.setColor(PALETTE[colorIndex % PALETTE.length]);
			g.fillArc(centerX - radius, centerY - radius, radius * 2, radius * 2,
					(int) startAngle, (int) -sweepAngle);
			startAngle -= sweepAngle;
			colorIndex++;
		}

		g.setColor(Color.WHITE);
		g.fillOval(centerX - radius / 3, centerY - radius / 3, radius * 2 / 3, radius * 2 / 3);

		int legendX = 340;
		int legendY = 30;
		g.setFont(new Font("Helvetica", Font.PLAIN, 10));
		colorIndex = 0;
		for (var entry : typeCounts.entrySet()) {
			g.setColor(PALETTE[colorIndex % PALETTE.length]);
			g.fillRect(legendX, legendY, 12, 12);
			g.setColor(new Color(60, 60, 60));
			String label = humanize(entry.getKey().name()) + " (" + entry.getValue() + ")";
			g.drawString(label, legendX + 18, legendY + 10);
			legendY += 20;
			colorIndex++;
		}

		g.dispose();
		return toBase64(image);
	}

	private static String humanize(String enumName) {
		var builder = new StringBuilder();
		for (var word : enumName.toLowerCase().split("_")) {
			if (builder.length() != 0)
				builder.append(' ');
			builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return builder.toString();
	}

	private static String toBase64(BufferedImage image) {
		try {
			var os = new ByteArrayOutputStream();
			ImageIO.write(image, "png", os);
			return java.util.Base64.getEncoder().encodeToString(os.toByteArray());
		} catch (Exception e) {
			return "";
		}
	}

}

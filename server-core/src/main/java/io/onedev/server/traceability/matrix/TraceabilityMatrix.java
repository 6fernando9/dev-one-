package io.onedev.server.traceability.matrix;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import io.onedev.server.traceability.ConfigItemType;

/**
 * Representa la Matriz de Trazabilidad Integral consolidada para un proyecto en una revisión dada.
 */
public class TraceabilityMatrix implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Long projectId;
    private final String revision;
    private final List<TraceabilityRow> rows;
    private final List<TraceabilityLink> links;
    private final Date calculatedAt;

    private final int totalRequirements;
    private final int synchronizedCount;
    private final int partialCount;
    private final int driftCount;
    private final int orphanCount;
    private final double coveragePercentage;

    public TraceabilityMatrix(Long projectId,
                              String revision,
                              List<TraceabilityRow> rows,
                              List<TraceabilityLink> links) {
        this.projectId = projectId;
        this.revision = revision != null ? revision : "HEAD";
        this.rows = rows != null ? new ArrayList<>(rows) : new ArrayList<>();
        this.links = links != null ? new ArrayList<>(links) : new ArrayList<>();
        this.calculatedAt = new Date();

        int totalReq = 0;
        int syncd = 0;
        int part = 0;
        int drift = 0;
        int orphans = 0;

        for (TraceabilityRow row : this.rows) {
            if (row.getPrimaryItem().getType() == ConfigItemType.REQUIREMENT) {
                totalReq++;
                if (row.getStatus() == TraceabilitySyncStatus.SYNCHRONIZED) {
                    syncd++;
                } else if (row.getStatus() == TraceabilitySyncStatus.PARTIAL) {
                    part++;
                } else {
                    drift++;
                }
            } else {
                orphans++;
            }
        }

        this.totalRequirements = totalReq;
        this.synchronizedCount = syncd;
        this.partialCount = part;
        this.driftCount = drift;
        this.orphanCount = orphans;
        this.coveragePercentage = totalReq > 0 ? (syncd * 100.0) / totalReq : 0.0;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getRevision() {
        return revision;
    }

    public List<TraceabilityRow> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public List<TraceabilityLink> getLinks() {
        return Collections.unmodifiableList(links);
    }

    public Date getCalculatedAt() {
        return calculatedAt;
    }

    public int getTotalRequirements() {
        return totalRequirements;
    }

    public int getSynchronizedCount() {
        return synchronizedCount;
    }

    public int getPartialCount() {
        return partialCount;
    }

    public int getDriftCount() {
        return driftCount;
    }

    public int getOrphanCount() {
        return orphanCount;
    }

    public double getCoveragePercentage() {
        return coveragePercentage;
    }

    public int getTotalLinks() {
        return links.size();
    }
}
package io.onedev.server.ai.tools.issue;

import static io.onedev.server.ai.ToolUtils.convertToJson;

import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.subject.Subject;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.agent.tool.ToolSpecification;
import io.onedev.server.OneDev;
import io.onedev.server.ai.IssueHelper;
import io.onedev.server.ai.TaskTool;
import io.onedev.server.ai.ToolExecutionResult;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.service.IssueService;

public final class GetIssue implements TaskTool {

	private final long issueId;

	public GetIssue(long issueId) {
		this.issueId = issueId;
	}

	@Override
	public ToolSpecification getSpecification() {
		return ToolSpecification.builder()
				.name("getIssue")
				.description("Get info of issue in json format")
				.build();
	}

	@Override
	public ToolExecutionResult execute(Subject subject, JsonNode arguments) {
		var issue = OneDev.getInstance(IssueService.class).load(issueId);
		if (!SecurityUtils.canAccessIssue(subject, issue))
			throw new UnauthorizedException();

		return new ToolExecutionResult(convertToJson(IssueHelper.getDetail(issue.getProject(), issue)), false);
	}
	
}

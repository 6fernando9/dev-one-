package io.onedev.server.ai.tools.codecomment;

import static io.onedev.server.ai.ToolUtils.convertToJson;

import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.subject.Subject;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.agent.tool.ToolSpecification;
import io.onedev.server.OneDev;
import io.onedev.server.ai.CodeCommentHelper;
import io.onedev.server.ai.TaskTool;
import io.onedev.server.ai.ToolExecutionResult;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.service.CodeCommentService;

public final class GetCodeCommentReplies implements TaskTool {

	private final long commentId;

	public GetCodeCommentReplies(long commentId) {
		this.commentId = commentId;
	}

	@Override
	public ToolSpecification getSpecification() {
		return ToolSpecification.builder()
				.name("getCodeCommentReplies")
				.description("Get replies of code comment in json format")
				.build();
	}

	@Override
	public ToolExecutionResult execute(Subject subject, JsonNode arguments) {
		var comment = OneDev.getInstance(CodeCommentService.class).load(commentId);
		if (!SecurityUtils.canReadCode(subject, comment.getProject()))
			throw new UnauthorizedException();
		return new ToolExecutionResult(convertToJson(CodeCommentHelper.getReplies(comment)), false);
	}
}

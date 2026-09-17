package io.onedev.server.security;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.codec.Base64;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.util.WebUtils;

import io.onedev.commons.utils.StringUtils;
import io.onedev.k8shelper.KubernetesHelper;
import io.onedev.server.persistence.annotation.Sessional;
import io.onedev.server.service.AccessTokenService;
import io.onedev.server.service.AuditEventService;

@Singleton
public class BasicAuthenticationFilter extends ExceptionHandleFilter {
	
	private final AccessTokenService accessTokenService;

	private final AuditEventService auditEventService;
	
	@Inject
	public BasicAuthenticationFilter(AccessTokenService accessTokenService,
			AuditEventService auditEventService) {
		this.accessTokenService = accessTokenService;
		this.auditEventService = auditEventService;
	}
	
	@Sessional
    @Override
	protected boolean onPreHandle(ServletRequest request, ServletResponse response, Object mappedValue) {
		HttpServletRequest httpRequest = WebUtils.toHttp(request);

    	Subject subject = SecurityUtils.getSubject();
		if (!subject.isAuthenticated()) {
	        String authzHeader = httpRequest.getHeader(KubernetesHelper.AUTHORIZATION);
			if (authzHeader == null)
				authzHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
	        if (authzHeader != null && authzHeader.toLowerCase().startsWith("basic ")) {
            	String authValue = StringUtils.substringAfter(authzHeader, " ");
                String decoded = Base64.decodeToString(authValue);
                String userName = StringUtils.substringBefore(decoded, ":").trim();
                String password = StringUtils.substringAfter(decoded, ":").trim();
				if (userName.length() != 0) {
					var accessToken = accessTokenService.findByValue(userName);
					if (accessToken != null) {
						ThreadContext.bind(accessToken.asSubject());
						return true;
					}
				}
				if (password.length() != 0) {
					var accessToken = accessTokenService.findByValue(password);
					if (accessToken != null) {
						ThreadContext.bind(accessToken.asSubject());
						return true;
					}
				}
			if (userName.length() != 0 && password.length() != 0) {
				try {
					subject.login(new UsernamePasswordToken(userName, password));
				} catch (AuthenticationException e) {
					auditEventService.recordLoginFailed(userName, "basic auth");
					throw e;
				}
				return true;
			}
	        }
		}
		return true;
	}

}

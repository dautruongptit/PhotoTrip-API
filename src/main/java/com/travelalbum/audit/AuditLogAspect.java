package com.travelalbum.audit;

import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.storage.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final HttpServletRequest request;

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        Long userId = currentUserIdOrNull();
        Object targetId = extractTargetId(pjp);
        String ip = ClientIpUtils.resolveIp(request);
        String userAgent = request.getHeader("User-Agent");

        try {
            Object result = pjp.proceed();
            auditLogService.log(userId, auditable.action(), auditable.targetType(),
                    targetId, ip, userAgent, "SUCCESS");
            return result;
        } catch (Exception ex) {
            auditLogService.log(userId, auditable.action(), auditable.targetType(),
                    targetId, ip, userAgent, "FAILED");
            throw ex;
        }
    }

    private Long currentUserIdOrNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) {
            return null;
        }
        return p.getId();
    }

    private Object extractTargetId(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String[] paramNames = sig.getParameterNames();
        Object[] args = pjp.getArgs();
        for (int i = 0; i < paramNames.length; i++) {
            if (paramNames[i].equals("id") || paramNames[i].equals("eventId")) {
                return args[i];
            }
        }
        return null;
    }
}
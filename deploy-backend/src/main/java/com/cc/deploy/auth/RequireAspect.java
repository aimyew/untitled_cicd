package com.cc.deploy.auth;

import com.cc.deploy.common.ForbiddenException;
import com.cc.deploy.entity.User;
import com.cc.deploy.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 权限校验切面：拦截标注了 @RequirePerm 的方法，判断当前用户是否有对应权限
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RequireAspect {

    private final PermissionService permissionService;
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Before("@annotation(requirePerm)")
    public void check(JoinPoint jp, RequirePerm requirePerm) {
        User user = UserContext.current();
        if (user == null) {
            throw new ForbiddenException("未登录，无法访问");
        }
        // 超管直接放行
        if (user.isSuperAdmin()) return;

        String code = resolveCode(requirePerm.value(), jp);
        if (!permissionService.hasPerm(user.getId(), code)) {
            throw new ForbiddenException("无权限: " + code);
        }
    }

    /** 解析 "project:deploy:#projectId" 这种表达式，从方法参数中取实际值 */
    private String resolveCode(String template, JoinPoint jp) {
        if (template == null || !template.contains("#")) return template;
        MethodSignature signature = (MethodSignature) jp.getSignature();
        Method method = signature.getMethod();
        String[] names = nameDiscoverer.getParameterNames(method);
        if (names == null) return template;
        Object[] args = jp.getArgs();
        String resolved = template;
        for (int i = 0; i < names.length; i++) {
            if (args[i] != null) {
                resolved = resolved.replace("#" + names[i], args[i].toString());
            }
        }
        return resolved;
    }
}

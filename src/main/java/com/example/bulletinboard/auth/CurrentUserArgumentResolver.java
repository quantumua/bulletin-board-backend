package com.example.bulletinboard.auth;

import com.example.bulletinboard.config.AuthenticatedAccount;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves a {@link CurrentUser} controller parameter from the security context only. Nothing is ever
 * read from the request body, parameters or headers (ADR-005, NFR5.2).
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Long accountId = AuthenticatedAccount.idOrNull();
        return accountId == null ? CurrentUser.anonymous() : CurrentUser.of(accountId);
    }
}

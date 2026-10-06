package com.example.bulletinboard.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationEntryPoint authenticationEntryPoint,
                                           CsrfTokenRepository csrfTokenRepository) {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/ads/**", "/h2-console/**", "/error").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // Ads are anonymous, so there is no session state to forge there yet
                .csrf(csrf -> csrf.spa()
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers("/api/ads/**", "/h2-console/**"))
                // The token is deferred; load it on every request so the XSRF-TOKEN cookie reaches the SPA
                // before its first state-changing call (e.g. on the initial GET /api/auth/me)
                .addFilterAfter((request, response, chain) -> {
                    if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
                        token.getToken();
                    }
                    chain.doFilter(request, response);
                }, CsrfFilter.class)
                .cors(Customizer.withDefaults())
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                // Sign-in and sign-out are handled by AuthController
                .logout(logout -> logout.disable())
                .requestCache(requestCache -> requestCache.disable())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint));
        return http.build();
    }

    /**
     * Answers unauthenticated requests with a 401 problem detail, rendered by Spring MVC like any other API error.
     */
    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        return (request, response, exception) -> {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication required");
            exceptionResolver.resolveException(request, response, null,
                    new ErrorResponseException(HttpStatus.UNAUTHORIZED, problem, exception));
        };
    }

    /**
     * The same cookie repository {@code csrf.spa()} would create, shared so AuthController can rotate the token on sign-in.
     */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}

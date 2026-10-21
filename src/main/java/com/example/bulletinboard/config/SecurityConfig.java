package com.example.bulletinboard.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Request security for the bulletin board.
 *
 * <ul>
 *   <li><b>Sessions (ADR-001):</b> server-side {@code HttpSession}s hold the security context
 *       ({@link HttpSessionSecurityContextRepository}). The session cookie keeps the servlet default name
 *       {@code JSESSIONID}; it is HttpOnly, SameSite=Lax, Secure over HTTPS, and has no expiry (see
 *       {@code application.properties}). Sign-up and log-in are served by
 *       {@link com.example.bulletinboard.auth.AuthController}, which changes the session id; form log-in,
 *       HTTP Basic and the built-in log-out filter are disabled.</li>
 *   <li><b>Anti-forgery (ADR-006):</b> double-submit cookie. {@code XSRF-TOKEN} is script-readable and
 *       must be echoed in the {@code X-XSRF-TOKEN} header on every POST and PUT, including sign-up,
 *       log-in and log-out. The plain {@link CsrfTokenRequestAttributeHandler} compares the raw cookie
 *       value, and {@link CsrfCookieFilter} makes sure the cookie is issued on every response lacking one.</li>
 *   <li><b>Authorisation (NFR5.1):</b> reading ads and the auth endpoints are open; creating and
 *       editing ads on both {@code /api/v1/ads} and the legacy {@code /api/ads} require a session; every
 *       other request is denied. Ownership is checked in {@link com.example.bulletinboard.ad.AdService}
 *       (ADR-005), never here.</li>
 *   <li><b>Errors (NFR7.2):</b> 401 and 403 are problem details with a {@code code}
 *       ({@link SecurityProblemHandler}).</li>
 *   <li><b>H2 console (accepted risk, requirements C3/OQ1):</b> a separate chain leaves
 *       {@code /h2-console/**} open, without CSRF, and frameable by the same origin, exactly as before.
 *       It must be closed before any deployment beyond trusted environments.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    static final String H2_CONSOLE = "/h2-console/**";

    @Bean
    @Order(1)
    public SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(H2_CONSOLE)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository, SecurityProblemHandler problemHandler) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ads/**", "/api/ads/**", "/api/v1/auth/me").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/ads/**", "/api/ads/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/ads/**", "/api/ads/**").authenticated()
                        .anyRequest().denyAll());
        return http.build();
    }

    /**
     * The {@code XSRF-TOKEN} cookie: readable by the same-origin frontend script, SameSite=Lax, Secure
     * over HTTPS (the repository's default follows the request).
     */
    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * No user-name/password users exist outside {@link com.example.bulletinboard.account.AccountService}.
     * This empty manager stops Spring Boot from creating its default user with a generated password.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}

package com.example.bulletinboard.support;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Signs users up and logs them in through the real API, keeping the session and the current
 * anti-forgery token the way a browser would.
 */
public final class TestAccounts {

    public static final String XSRF_COOKIE = "XSRF-TOKEN";
    public static final String XSRF_HEADER = "X-XSRF-TOKEN";

    private TestAccounts() {
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    public static String markerPassword() {
        return "Marker-P4ss-" + UUID.randomUUID();
    }

    /**
     * Reads a fresh anti-forgery token the way the frontend does on page load.
     */
    public static Cookie fetchXsrfCookie(MockMvc mockMvc) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")).andReturn();
        Cookie cookie = result.getResponse().getCookie(XSRF_COOKIE);
        if (cookie == null) {
            throw new IllegalStateException("GET /api/v1/auth/me did not issue an XSRF-TOKEN cookie");
        }
        return cookie;
    }

    /**
     * Sends the token both as cookie and header, like the frontend.
     */
    public static RequestPostProcessor xsrf(Cookie xsrfCookie) {
        return request -> {
            addCookie(request, xsrfCookie);
            request.addHeader(XSRF_HEADER, xsrfCookie.getValue());
            return request;
        };
    }

    public static User signUp(MockMvc mockMvc, String displayName) throws Exception {
        return signUp(mockMvc, uniqueEmail(), displayName, markerPassword());
    }

    public static User signUp(MockMvc mockMvc, String email, String displayName, String password) throws Exception {
        Cookie xsrf = fetchXsrfCookie(mockMvc);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .with(xsrf(xsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "displayName": "%s", "password": "%s"}
                                """.formatted(email, displayName, password)))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("Sign-up failed with " + result.getResponse().getStatus()
                    + ": " + result.getResponse().getContentAsString());
        }
        return toUser(result, email, password);
    }

    public static User logIn(MockMvc mockMvc, String email, String password) throws Exception {
        Cookie xsrf = fetchXsrfCookie(mockMvc);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(xsrf(xsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Log-in failed with " + result.getResponse().getStatus());
        }
        return toUser(result, email, password);
    }

    private static User toUser(MvcResult result, String email, String password) throws Exception {
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        Cookie rotatedXsrf = result.getResponse().getCookie(XSRF_COOKIE);
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        return new User(id, email, password, session, rotatedXsrf);
    }

    private static void addCookie(MockHttpServletRequest request, Cookie cookie) {
        Cookie[] existing = request.getCookies();
        Cookie[] cookies = existing == null ? new Cookie[1] : java.util.Arrays.copyOf(existing, existing.length + 1);
        cookies[cookies.length - 1] = cookie;
        request.setCookies(cookies);
    }

    /**
     * A logged-in user: their session and the anti-forgery token issued with it.
     */
    public record User(long id, String email, String password, MockHttpSession session, Cookie xsrfCookie) {

        /**
         * Sends this user's session and anti-forgery token, like the browser of a logged-in user.
         */
        public RequestPostProcessor loggedIn() {
            return request -> {
                request.setSession(session);
                return xsrf(xsrfCookie).postProcessRequest(request);
            };
        }
    }
}

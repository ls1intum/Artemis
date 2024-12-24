package de.tum.cit.aet.artemis.core.security.allowedTools;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.jwt.JWTFilter;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class AllowedToolsTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "allowedtools";

    @Autowired
    private TokenProvider tokenProvider;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    @Test
    void testAllowedToolsRouteWithToolToken() throws Exception {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password",
                Collections.singletonList(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));

        String jwt = tokenProvider.createToken(authentication, 24 * 60 * 60 * 1000, ToolTokenType.SCORPIO);
        Cookie cookie = new Cookie(JWTFilter.JWT_COOKIE_NAME, jwt);

        request.performMvcRequest(get("/api/test/testAllowedToolTokenScorpio").cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAllowedToolsRouteWithGeneralToken() throws Exception {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password",
                Collections.singletonList(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));

        String jwt = tokenProvider.createToken(authentication, 24 * 60 * 60 * 1000, null);
        Cookie cookie = new Cookie(JWTFilter.JWT_COOKIE_NAME, jwt);

        request.performMvcRequest(get("/api/test/testAllowedToolTokenScorpio").cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAllowedToolsRouteWithDifferentToolToken() throws Exception {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password",
                Collections.singletonList(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));

        String jwt = tokenProvider.createToken(authentication, 24 * 60 * 60 * 1000, ToolTokenType.SCORPIO);
        Cookie cookie = new Cookie(JWTFilter.JWT_COOKIE_NAME, jwt);

        request.performMvcRequest(get("/api/test/testNoAllowedToolToken").cookie(cookie)).andExpect(status().isForbidden());
    }

}

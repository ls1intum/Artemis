package de.tum.in.www1.artemis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import javax.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import de.tum.in.www1.artemis.security.jwt.JWTCookieService;
import de.tum.in.www1.artemis.security.jwt.JWTFilter;
import de.tum.in.www1.artemis.web.rest.ClientForwardResource;

/**
 * Test class for the ClientForwardController REST controller.
 *
 * @see ClientForwardResource
 */
class ClientForwardTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private JWTCookieService jwtCookieService;

    @Test
    void testClientEndpoint() throws Exception {
        ResultActions perform = request.getMvc().perform(get("/non-existent-mapping"));
        perform.andExpect(status().isOk()).andExpect(forwardedUrl("/"));
    }

    @Test
    void testNestedClientEndpoint() throws Exception {
        request.getMvc().perform(get("/admin/user-management")).andExpect(status().isOk()).andExpect(forwardedUrl("/"));
    }

    @Test
    void getUnmappedDottedEndpoint() throws Exception {
        request.getMvc().perform(get("/foo.js")).andExpect(status().isNotFound());
    }

    @Test
    void getUnmappedNestedDottedEndpoint() throws Exception {
        request.getMvc().perform(get("/foo/bar.js")).andExpect(status().isNotFound());
    }

    @Test
    void getWebsocketInfoEndpoint() throws Exception {
        request.getMvc().perform(get("/websocket/info")).andExpect(status().isOk());
    }

    @Test
    void getWebsocketEndpointFailedHandshakeNoCookie() throws Exception {
        request.getMvc().perform(get("/websocket/308/sessionId/websocket")).andExpect(status().isOk()); // Failed handshake without cookie returns 200
    }

    @Test
    void getWebsocketEndpointWithInvalidCookie() throws Exception {
        Cookie cookie = new Cookie(JWTFilter.JWT_COOKIE_NAME, "invalidCookie");
        request.getMvc().perform(get("/websocket/308/sessionId/websocket").cookie(cookie)).andExpect(status().isOk()); // Failed handshake with invalid cookie returns 200
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getWebsocketEndpointWithCookie() throws Exception {
        ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(true);
        Cookie cookie = new Cookie(responseCookie.getName(), responseCookie.getValue());
        request.getMvc().perform(get("/websocket/308/sessionId/websocket").cookie(cookie)).andExpect(status().isBadRequest())
                .andExpect(content().string("Can \"Upgrade\" only to \"WebSocket\".")); // Handshake is successfull but connection fails to upgrade using MockMvc
    }

    @Test
    void getWebsocketFallbackEndpoint() throws Exception {
        request.getMvc().perform(get("/websocket/308/sessionId/xhr_streaming")).andExpect(status().isNotFound());
    }
}

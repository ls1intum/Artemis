package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import javax.servlet.http.Cookie;

import de.tum.in.www1.artemis.web.rest.dto.LtiLaunchRequestDTO;

public class AuthenticationIntegrationTestHelper {

    public static final String LTI_USER_EMAIL = "tester@tum.invalid";

    public static final String LTI_USER_EMAIL_UPPER_CASE = "Tester@Tum.Invalid";

    public static LtiLaunchRequestDTO setupDefaultLtiLaunchRequest() {
        LtiLaunchRequestDTO ltiLaunchRequest = new LtiLaunchRequestDTO();
        ltiLaunchRequest.setContext_id("contextId123");
        ltiLaunchRequest.setContext_label("U4I");
        ltiLaunchRequest.setCustom_component_display_name("someDisplayName");
        ltiLaunchRequest.setLaunch_presentation_locale("EN");
        ltiLaunchRequest.setLaunch_presentation_return_url("some.return.url.com");
        ltiLaunchRequest.setLis_outcome_service_url("some.outcome.service.url.com");
        ltiLaunchRequest.setLis_person_contact_email_primary(LTI_USER_EMAIL);
        ltiLaunchRequest.setLis_person_sourcedid("somePersonSourceId");
        ltiLaunchRequest.setLis_result_sourcedid("someResultSourceId");
        ltiLaunchRequest.setLti_message_type("someMessageType");
        ltiLaunchRequest.setLti_version("1.0.0");
        ltiLaunchRequest.setOauth_consumer_key("artemis_lti_key");
        ltiLaunchRequest.setOauth_nonce("171298047571430710991572204884");
        ltiLaunchRequest.setOauth_signature("GYXApaIv0x7k/OPT9/oU38IBQRc=");
        ltiLaunchRequest.setOauth_signature_method("HMAC-SHA1");
        ltiLaunchRequest.setOauth_timestamp(1572204884L);
        ltiLaunchRequest.setOauth_version("1.0");
        ltiLaunchRequest.setResource_link_id("courses.u4i.com-16a90aca094448ab95caf484b5c35d32");
        ltiLaunchRequest.setRoles("Student");
        ltiLaunchRequest.setUser_id("ff30145d6884eeb2c1cef50298939383");
        return ltiLaunchRequest;
    }

    public static void authenticationCookieAssertions(Cookie cookie, boolean logoutCookie) {
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");

        if (logoutCookie) {
            assertThat(cookie.getMaxAge()).isZero();
            assertThat(cookie.getValue()).isEmpty();
        }
        else {
            assertThat(cookie.getMaxAge()).isGreaterThan(0);
            assertThat(cookie.getValue()).isNotEmpty();
        }
    }
}

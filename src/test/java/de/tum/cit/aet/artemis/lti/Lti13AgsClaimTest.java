package de.tum.cit.aet.artemis.lti;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import de.tum.cit.aet.artemis.lti.dto.Claims;
import de.tum.cit.aet.artemis.lti.dto.Lti13AgsClaim;

class Lti13AgsClaimTest {

    private static Optional<Lti13AgsClaim> claimOf(Object agsClaim) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "subject");
        if (agsClaim != null) {
            claims.put(Claims.AGS_CLAIM, agsClaim);
        }
        return Lti13AgsClaim.from(new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), claims));
    }

    @Test
    void noClaimGivesNoAgsClaim() {
        assertThat(claimOf(null)).isEmpty();
    }

    @Test
    void readsTheLineItemOfMoodle() {
        assertThat(claimOf(Map.of("lineitem", "https://lms/line/1", "scope", List.of("a")))).contains(new Lti13AgsClaim("https://lms/line/1"));
    }

    @Test
    void readsTheLineItemsOfEdX() {
        assertThat(claimOf(Map.of("lineitems", "https://lms/lines"))).contains(new Lti13AgsClaim("https://lms/lines"));
    }

    @Test
    void preferTheLineItemOverTheLineItems() {
        assertThat(claimOf(Map.of("lineitem", "https://lms/line/1", "lineitems", "https://lms/lines"))).contains(new Lti13AgsClaim("https://lms/line/1"));
    }

    @Test
    void aMalformedClaimMeansNoLineItemInsteadOfAFailedLaunch() {
        assertThat(claimOf("not an object")).contains(new Lti13AgsClaim(null));
        assertThat(claimOf(List.of("a"))).contains(new Lti13AgsClaim(null));
        assertThat(claimOf(Map.of("lineitem", Map.of("nested", 1)))).contains(new Lti13AgsClaim(null));
        assertThat(claimOf(Map.of("lineitem", "https://lms/line/1", "lineitems", List.of("a")))).contains(new Lti13AgsClaim("https://lms/line/1"));
        assertThat(claimOf(Map.of("lineitems", Map.of("nested", 1)))).contains(new Lti13AgsClaim(null));
    }
}

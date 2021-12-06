package de.tum.in.www1.artemis;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import de.tum.in.www1.artemis.config.migration.MigrationRegistry;

@SpringBootTest(properties = { "artemis.athene.token-validity-in-seconds=10800",
        "artemis.athene.base64-secret=YWVuaXF1YWRpNWNlaXJpNmFlbTZkb283dXphaVF1b29oM3J1MWNoYWlyNHRoZWUzb2huZ2FpM211bGVlM0VpcAo=" })
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "bamboo", "bitbucket", "jira", "ldap", "scheduling", "athene", "apollon" })
public class MigrationIntegrityTest {

    @Autowired
    private MigrationRegistry registry;

    @Test
    public void testIntegrity() {
        registry.instantiateEntryMap();

        // assertThat(registry.checkIntegrity()).isTrue();
    }
}

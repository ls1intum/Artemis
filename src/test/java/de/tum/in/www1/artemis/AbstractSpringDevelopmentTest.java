package de.tum.in.www1.artemis;

import static io.github.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "dev", "scheduling" })
@TestPropertySource(properties = { "artemis.user-management.use-external=false", "artemis.version-control.ssh-private-key-folder-path=./src/test/resources/sshtestkey",
        "artemis.version-control.ssh-private-key-password=test1234" })
public abstract class AbstractSpringDevelopmentTest {

    @Autowired
    protected DatabaseUtilService database;

    @Autowired
    protected RequestUtilService request;
}

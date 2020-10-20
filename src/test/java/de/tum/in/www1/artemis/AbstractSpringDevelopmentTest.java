package de.tum.in.www1.artemis;

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
@ActiveProfiles({ "artemis", "dev", "scheduling" })
@TestPropertySource(properties = "artemis.user-management.use-external=false")
public class AbstractSpringDevelopmentTest {

    @Autowired
    protected DatabaseUtilService database;

    @Autowired
    protected RequestUtilService request;
}

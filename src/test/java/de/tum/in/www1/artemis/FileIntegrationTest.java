package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.web.firewall.RequestRejectedException;

import de.tum.in.www1.artemis.util.RequestUtilService;

public class FileIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    private RequestUtilService request;

    @Test
    @WithAnonymousUser
    public void getPublicFile_relativePathOutsideOfPublicDir_forbidden() throws Exception {
        assertThatExceptionOfType(RequestRejectedException.class)
                .isThrownBy(() -> request.get("/api/files/public/content/..%2F..%2Frepos%2Fabc.txt", HttpStatus.FORBIDDEN, String.class));
    }
}

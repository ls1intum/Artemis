package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import de.tum.in.www1.artemis.config.localvcci.ssh.MultipleHostKeyProvider;

@Profile(PROFILE_LOCALVC)
class MultipleHostKeyProviderTest extends LocalVCIntegrationTest {

    @Test
    void testMultipleHostKeyProvider() {
        MultipleHostKeyProvider multipleHostKeyProvider = new MultipleHostKeyProvider(Path.of("./"));

        multipleHostKeyProvider.loadKeys(null);
        assertThat(multipleHostKeyProvider.getKeySize()).isEqualTo(0);
    }
}

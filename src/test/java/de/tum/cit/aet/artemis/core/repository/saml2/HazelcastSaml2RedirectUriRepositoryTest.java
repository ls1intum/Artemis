package de.tum.cit.aet.artemis.core.repository.saml2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

class HazelcastSaml2RedirectUriRepositoryTest extends AbstractSpringIntegrationLocalVCSamlTest {

    @Autowired
    private HazelcastSaml2RedirectUriRepository repository;

    private static final String TEST_NONCE = "test-nonce-123";

    private static final String TEST_REDIRECT_URI = "vscode://artemis/callback";

    @AfterEach
    void cleanup() {
        repository.consumeAndRemove(TEST_NONCE);
    }

    @Test
    void testSaveAndConsume() {
        repository.save(TEST_NONCE, TEST_REDIRECT_URI);
        String result = repository.consumeAndRemove(TEST_NONCE);
        assertThat(result).isEqualTo(TEST_REDIRECT_URI);
    }

    @Test
    void testConsumeRemovesEntry() {
        repository.save(TEST_NONCE, TEST_REDIRECT_URI);
        repository.consumeAndRemove(TEST_NONCE);
        String result = repository.consumeAndRemove(TEST_NONCE);
        assertThat(result).isNull();
    }

    @Test
    void testConsumeNonExistentNonce() {
        String result = repository.consumeAndRemove("nonexistent-nonce");
        assertThat(result).isNull();
    }

    @Test
    void testMultipleNonces() {
        String nonce1 = "nonce-1";
        String nonce2 = "nonce-2";
        repository.save(nonce1, "vscode://callback1");
        repository.save(nonce2, "vscode://callback2");

        assertThat(repository.consumeAndRemove(nonce1)).isEqualTo("vscode://callback1");
        assertThat(repository.consumeAndRemove(nonce2)).isEqualTo("vscode://callback2");
    }
}

package de.tum.cit.aet.artemis.core.security.saml2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import de.tum.cit.aet.artemis.core.repository.saml2.HazelcastSaml2RedirectUriRepository;

/**
 * Pure unit tests for {@link Saml2RelayStateResolver} (no Spring context). The Hazelcast-backed
 * repository is mocked, so the resolver logic can be exercised in isolation.
 */
class Saml2RelayStateResolverTest {

    private final HazelcastSaml2RedirectUriRepository redirectUriRepository = mock(HazelcastSaml2RedirectUriRepository.class);

    private Saml2RelayStateResolver resolverWithSchemes(String... allowedSchemes) {
        return new Saml2RelayStateResolver(new SAML2RedirectUriValidator(List.of(allowedSchemes)), redirectUriRepository);
    }

    private MockHttpServletRequest requestWithRedirectUri(String redirectUri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("redirect_uri", redirectUri);
        return request;
    }

    @Test
    void testReturnsNullWhenNoRedirectUri() {
        assertThat(resolverWithSchemes("vscode").convert(new MockHttpServletRequest())).isNull();
        verify(redirectUriRepository, never()).save(any(), any());
    }

    @Test
    void testReturnsNullWhenRedirectUriBlank() {
        assertThat(resolverWithSchemes("vscode").convert(requestWithRedirectUri("   "))).isNull();
        verify(redirectUriRepository, never()).save(any(), any());
    }

    @Test
    void testReturnsNullWhenFeatureDisabled() {
        assertThat(resolverWithSchemes().convert(requestWithRedirectUri("vscode://artemis/callback"))).isNull();
        verify(redirectUriRepository, never()).save(any(), any());
    }

    @Test
    void testReturnsNullWhenRedirectUriInvalid() {
        assertThat(resolverWithSchemes("vscode").convert(requestWithRedirectUri("https://evil.com/steal"))).isNull();
        verify(redirectUriRepository, never()).save(any(), any());
    }

    @Test
    void testReturnsNonceAndStoresValidRedirectUri() {
        String redirectUri = "vscode://artemis/callback";
        String nonce = resolverWithSchemes("vscode").convert(requestWithRedirectUri(redirectUri));

        assertThat(nonce).isNotNull();
        assertThatCode(() -> UUID.fromString(nonce)).doesNotThrowAnyException();

        ArgumentCaptor<String> nonceCaptor = ArgumentCaptor.forClass(String.class);
        verify(redirectUriRepository).save(nonceCaptor.capture(), eq(redirectUri));
        assertThat(nonceCaptor.getValue()).isEqualTo(nonce);
    }
}

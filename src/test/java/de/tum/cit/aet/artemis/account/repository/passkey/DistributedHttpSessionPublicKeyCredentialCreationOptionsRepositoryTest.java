package de.tum.cit.aet.artemis.account.repository.passkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.dto.passkey.PublicKeyCredentialCreationOptionsDTO;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

class DistributedHttpSessionPublicKeyCredentialCreationOptionsRepositoryTest {

    private DistributedMap<String, PublicKeyCredentialCreationOptionsDTO> map;

    private DistributedHttpSessionPublicKeyCredentialCreationOptionsRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        var provider = mock(DistributedDataProvider.class);
        map = mock(DistributedMap.class);
        when(provider.<String, PublicKeyCredentialCreationOptionsDTO>getExpiringMap(any(), any(Duration.class))).thenReturn(map);
        repository = new DistributedHttpSessionPublicKeyCredentialCreationOptionsRepository(provider);
    }

    @Test
    void load_noCachedOptions_returnsNull() {
        var request = mock(HttpServletRequest.class);
        when(request.getRemoteUser()).thenReturn("student1");
        when(map.get("student1")).thenReturn(null);

        assertThat(repository.load(request)).isNull();
        verify(map).get(eq("student1"));
    }

    @Test
    void load_unauthenticatedUser_returnsNull() {
        var request = mock(HttpServletRequest.class);
        when(request.getRemoteUser()).thenReturn(null);

        assertThat(repository.load(request)).isNull();
    }
}

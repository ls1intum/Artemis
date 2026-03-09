package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.service.CompetencyExpertToolsService.CompetencyOperation;

@ExtendWith(MockitoExtension.class)
class AtlasAgentSessionCacheServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache operationsCache;

    @Mock
    private Cache relationsCache;

    private AtlasAgentSessionCacheService service;

    private static final String SESSION_ID = "course_1_user_1";

    @BeforeEach
    void setUp() {
        service = new AtlasAgentSessionCacheService(cacheManager);
    }

    @Test
    void shouldStoreAndRetrieveCompetencyOperations() {
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_OPERATIONS_CACHE)).thenReturn(operationsCache);
        List<CompetencyOperation> operations = List.of(new CompetencyOperation(null, "OOP", "Object-Oriented Programming", CompetencyTaxonomy.APPLY));

        service.cachePendingCompetencyOperations(SESSION_ID, operations);

        verify(operationsCache).put(SESSION_ID, operations);
    }

    @Test
    void shouldReturnNullWhenCompetencyOperationsCacheNotConfigured() {
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_OPERATIONS_CACHE)).thenReturn(null);

        List<CompetencyOperation> result = service.getCachedPendingCompetencyOperations(SESSION_ID);

        assertThat(result).isNull();
    }

    @Test
    void shouldEvictCompetencyOperations() {
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_OPERATIONS_CACHE)).thenReturn(operationsCache);

        service.clearCachedPendingCompetencyOperations(SESSION_ID);

        verify(operationsCache).evict(SESSION_ID);
    }

    @Test
    void shouldStoreAndRetrieveRelationData() {
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_RELATIONS_CACHE)).thenReturn(relationsCache);
        List<CompetencyRelationDTO> relations = List.of(new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES));

        service.cacheRelationOperations(SESSION_ID, relations);

        verify(relationsCache).put(SESSION_ID, relations);
    }

    @Test
    void shouldReturnNullWhenRelationCacheNotConfigured() {
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_RELATIONS_CACHE)).thenReturn(null);

        List<CompetencyRelationDTO> result = service.getCachedRelationData(SESSION_ID);

        assertThat(result).isNull();
    }

    @Test
    void shouldEvictRelationData() {
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_RELATIONS_CACHE)).thenReturn(relationsCache);

        service.clearCachedRelationOperations(SESSION_ID);

        verify(relationsCache).evict(SESSION_ID);
    }

    @Test
    void shouldNotThrowWhenCacheNotConfiguredOnPut() {
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_OPERATIONS_CACHE)).thenReturn(null);
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_RELATIONS_CACHE)).thenReturn(null);

        service.cachePendingCompetencyOperations(SESSION_ID, List.of());
        service.cacheRelationOperations(SESSION_ID, List.of());
        service.clearCachedPendingCompetencyOperations(SESSION_ID);
        service.clearCachedRelationOperations(SESSION_ID);
    }
}

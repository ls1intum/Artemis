package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.iris.dto.MemirisLearningDTO;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryConnectionDTO;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryDTO;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryWithRelationsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris.PyrisLearningDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris.PyrisMemoryConnectionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris.PyrisMemoryDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris.PyrisMemoryWithRelationsDTO;

class IrisMemoryResourceIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "memoryresourceit";

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private UserTestRepository userTestRepository;

    @BeforeEach
    void setupUsers() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        activateIrisGlobally();

        // Enable Memiris feature and user flag
        featureToggleService.enableFeature(Feature.Memiris);
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        userTestRepository.updateMemirisEnabled(user.getId(), true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMemories_shouldReturnItems() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var items = List.of(new MemirisMemoryDTO("A", "Title A", "Content A", List.of(), List.of(), false, false),
                new MemirisMemoryDTO("B", "Title B", "Content B", List.of("L1"), List.of("C1"), true, false));
        irisRequestMockProvider.mockListMemories(user.getId(), items);

        List<MemirisMemoryDTO> response = request.getList("/api/iris/memories/user", HttpStatus.OK, MemirisMemoryDTO.class);
        assertThat(response).hasSize(2);
        assertThat(response.get(0).id()).isEqualTo("A");
        assertThat(response.get(1).slept_on()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMemories_shouldReturnEmptyList() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        irisRequestMockProvider.mockListMemories(user.getId(), List.of());
        List<MemirisMemoryDTO> response = request.getList("/api/iris/memories/user", HttpStatus.OK, MemirisMemoryDTO.class);
        assertThat(response).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMemories_whenPyrisFails_shouldReturnInternalServerError() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        irisRequestMockProvider.mockListMemoriesError(user.getId(), HttpStatus.INTERNAL_SERVER_ERROR);
        request.getList("/api/iris/memories/user", HttpStatus.INTERNAL_SERVER_ERROR, MemirisMemoryDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMemoryWithRelations_shouldFlattenResponse() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var memoryId = "M-1";
        var pyrisMemory = new PyrisMemoryDTO(memoryId, "T", "C", true, false);
        var learning = new PyrisLearningDTO("L1", "LTitle", "LContent", "Ref", List.of(memoryId));
        var connected = new PyrisMemoryDTO("M-2", "Other", "OtherContent", false, false);
        var connection = new PyrisMemoryConnectionDTO("CN1", "related", List.of(connected), "desc", 0.8);
        var body = new PyrisMemoryWithRelationsDTO(pyrisMemory, List.of(learning), List.of(connection));
        irisRequestMockProvider.mockGetMemoryWithRelations(user.getId(), memoryId, body);

        var dto = request.get("/api/iris/memories/user/" + memoryId, HttpStatus.OK, MemirisMemoryWithRelationsDTO.class);
        assertThat(dto.id()).isEqualTo(memoryId);
        assertThat(dto.title()).isEqualTo("T");
        assertThat(dto.sleptOn()).isTrue();
        assertThat(dto.learnings()).extracting(MemirisLearningDTO::id).containsExactly("L1");
        assertThat(dto.connections()).extracting(MemirisMemoryConnectionDTO::connectionType).containsExactly("related");
        var firstConn = dto.connections().getFirst();
        assertThat(firstConn.memories()).containsExactly("M-2");
        assertThat(firstConn.weight()).isEqualTo(0.8);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMemoryWithRelations_whenNotFound_shouldReturnNotFound() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var memoryId = "missing";
        irisRequestMockProvider.mockGetMemoryWithRelationsError(user.getId(), memoryId, HttpStatus.NOT_FOUND);
        request.get("/api/iris/memories/user/" + memoryId, HttpStatus.NOT_FOUND, MemirisMemoryWithRelationsDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteMemory_shouldReturnNoContent() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var memoryId = "DEL-1";
        irisRequestMockProvider.mockDeleteMemory(user.getId(), memoryId);
        request.delete("/api/iris/memories/user/" + memoryId, HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteMemory_whenPyrisFails_shouldReturnInternalServerError() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var memoryId = "FAIL-1";
        irisRequestMockProvider.mockDeleteMemoryError(user.getId(), memoryId, HttpStatus.INTERNAL_SERVER_ERROR);
        request.delete("/api/iris/memories/user/" + memoryId, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

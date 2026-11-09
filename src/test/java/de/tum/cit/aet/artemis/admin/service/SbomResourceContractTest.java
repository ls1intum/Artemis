package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.admin.dto.SbomComponentDTO;
import de.tum.cit.aet.artemis.admin.dto.SbomDTO;

class SbomResourceContractTest {

    private final SbomService service = new SbomService(JsonMapper.builder().build());

    @Test
    void licenseIdsTakePrecedenceOverNamesWhileExpressionsAndNamedLicensesArePreserved() {
        var parsed = parse("""
                {"components":[{"group":"org.example","name":"library","version":"1.2.3","type":"library","purl":"pkg:maven/org.example/library@1.2.3",
                "description":"A library","licenses":[{"license":{"id":"MIT","name":"Ignored name"}},
                {"license":{"name":"Named license"}},{"license":{}},{"expression":"Apache-2.0 OR MIT"},{}]}]}
                """);
        assertThat(parsed.version()).isOne();
        assertThat(parsed.metadata()).isNull();
        assertThat(parsed.components()).containsExactly(new SbomComponentDTO("org.example", "library", "1.2.3", "library", "pkg:maven/org.example/library@1.2.3",
                List.of("MIT", "Named license", "Apache-2.0 OR MIT"), "A library"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "{\"components\":[]}", "{\"components\":null}" })
    void missingOrEmptyComponentCollectionsProduceAnEmptyInventory(String json) {
        var parsed = parse(json);
        assertThat(parsed.components()).isEmpty();
        assertThat(parsed.version()).isOne();
        assertThat(parsed.metadata()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "{\"licenses\":null}", "{\"licenses\":[]}" })
    void missingOrEmptyLicenseCollectionsDoNotInventALicense(String component) {
        var parsed = parse("{\"version\":2,\"metadata\":{},\"components\":[" + component + "]}");
        assertThat(parsed.version()).isEqualTo(2);
        assertThat(parsed.metadata()).isNotNull();
        assertThat(parsed.metadata().timestamp()).isNull();
        assertThat(parsed.metadata().componentName()).isNull();
        assertThat(parsed.metadata().version()).isNull();
        assertThat(parsed.components()).containsExactly(new SbomComponentDTO(null, null, null, null, null, List.of(), null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"components\":{}}", "{\"components\":[{\"licenses\":{}}]}" })
    void aCollectionThatIsNotAnArrayMakesTheDocumentUnreadable(String json) {
        assertThat(parse(json)).isNull();
    }

    @ParameterizedTest
    @CsvSource({ "false,false", "false,true", "true,false", "true,true" })
    void availabilityAndCombinedInventoryReflectWhichResourcesActuallyExist(boolean serverPresent, boolean clientPresent) {
        try (var resources = mockConstruction(ClassPathResource.class, (resource, context) -> {
            boolean server = context.arguments().getFirst().toString().contains("server-sbom");
            when(resource.exists()).thenReturn(server ? serverPresent : clientPresent);
            String name = server ? "server" : "client";
            when(resource.getInputStream()).thenAnswer(_ -> new ByteArrayInputStream(("{\"serialNumber\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8)));
        })) {
            assertThat(service.isSbomAvailable()).isEqualTo(serverPresent || clientPresent);
            var combined = service.getCombinedSbom();
            if (serverPresent) {
                assertThat(combined.server().serialNumber()).isEqualTo("server");
            }
            else {
                assertThat(combined.server()).isNull();
            }
            if (clientPresent) {
                assertThat(combined.client().serialNumber()).isEqualTo("client");
            }
            else {
                assertThat(combined.client()).isNull();
            }
        }
    }

    @Test
    void unreadableResourcesDegradeToNoInventory() {
        try (var resources = mockConstruction(ClassPathResource.class, (resource, _) -> {
            when(resource.exists()).thenReturn(true);
            when(resource.getInputStream()).thenThrow(new IOException("unreadable"));
        })) {
            assertThat(service.getServerSbom()).isNull();
            assertThat(service.getClientSbom()).isNull();
        }
    }

    private SbomDTO parse(String json) {
        try (var resources = mockConstruction(ClassPathResource.class, (resource, _) -> {
            when(resource.exists()).thenReturn(true);
            when(resource.getInputStream()).thenAnswer(_ -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        })) {
            return service.getServerSbom();
        }
    }
}

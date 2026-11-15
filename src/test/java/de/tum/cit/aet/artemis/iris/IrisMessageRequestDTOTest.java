package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.dto.IrisMessageRequestDTO;

class IrisMessageRequestDTOTest {

    @Test
    void shouldNormalizeNullContentAndUncommittedFilesToEmptyCollections() {
        var dto = new IrisMessageRequestDTO(null, null, null, null, null, null);

        assertThat(dto.content()).isEmpty();
        assertThat(dto.uncommittedFiles()).isEmpty();
    }

    @Test
    void shouldKeepProvidedContentAndUncommittedFiles() {
        var files = Map.of("src/Main.java", "class Main {}");
        var dto = new IrisMessageRequestDTO(List.of(), 3, files, null, null, "client");

        assertThat(dto.content()).isEmpty();
        assertThat(dto.uncommittedFiles()).isEqualTo(files);
        assertThat(dto.messageDifferentiator()).isEqualTo(3);
    }
}

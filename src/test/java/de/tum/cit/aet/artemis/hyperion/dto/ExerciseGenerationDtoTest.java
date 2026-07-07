package de.tum.cit.aet.artemis.hyperion.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Unit test for the three exercise-generation wire DTOs. It pins the behaviour the client and the reconnect/replay path depend on: the file-snapshot factory's repository
 * classification, whole-content hashing, and byte-boundary truncation; the event factories' terminal shape; and the {@code @JsonInclude(NON_EMPTY)} contract that keeps empty
 * optional fields off the wire. The wire shape is asserted by serialising with a real Jackson mapper (matching production), not by reading getters.
 */
class ExerciseGenerationDtoTest {

    private final JsonMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private static String sha256Hex(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- ExerciseGenerationFileSnapshotDTO -------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void fileSnapshot_classifiesRepositoryBucketFromTopLevelDirectory() {
        assertThat(ExerciseGenerationFileSnapshotDTO.of("solution/src/A.java", "create", "x", 1).repo()).isEqualTo("solution");
        assertThat(ExerciseGenerationFileSnapshotDTO.of("template/src/A.java", "create", "x", 1).repo()).isEqualTo("template");
        assertThat(ExerciseGenerationFileSnapshotDTO.of("tests/test/ATest.java", "create", "x", 1).repo()).isEqualTo("tests");
        // Anything not under one of the three repository roots (e.g. the top-level problem statement) is bucketed as "other".
        assertThat(ExerciseGenerationFileSnapshotDTO.of("problem-statement.md", "create", "x", 1).repo()).isEqualTo("other");
    }

    @Test
    void fileSnapshot_hashesFullContentAndReportsFullByteSize_forSmallContent() {
        String content = "int add(int a, int b) { return a + b; }";

        ExerciseGenerationFileSnapshotDTO snapshot = ExerciseGenerationFileSnapshotDTO.of("solution/Calc.java", ExerciseGenerationFileSnapshotDTO.ACTION_CREATE, content, 7);

        assertThat(snapshot.type()).isEqualTo(ExerciseGenerationFileSnapshotDTO.TYPE);
        assertThat(snapshot.path()).isEqualTo("solution/Calc.java");
        assertThat(snapshot.action()).isEqualTo("create");
        assertThat(snapshot.content()).isEqualTo(content);
        assertThat(snapshot.truncated()).isFalse();
        assertThat(snapshot.bytes()).isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
        assertThat(snapshot.turn()).isEqualTo(7);
        assertThat(snapshot.sha256()).isEqualTo(sha256Hex(content));
    }

    @Test
    void fileSnapshot_truncatesOversizedContent_butStillHashesTheWholeFile() {
        // A file larger than the cap must be truncated for streaming, but the sha256 and bytes must reflect the WHOLE file so a client can still detect change despite truncation.
        String fullContent = "a".repeat(ExerciseGenerationFileSnapshotDTO.MAX_CONTENT_BYTES + 5000);

        ExerciseGenerationFileSnapshotDTO snapshot = ExerciseGenerationFileSnapshotDTO.of("tests/Big.java", ExerciseGenerationFileSnapshotDTO.ACTION_EDIT, fullContent, 0);

        assertThat(snapshot.truncated()).isTrue();
        // Content is capped at the byte limit (ASCII: one byte per char) while bytes/sha256 describe the untruncated file.
        assertThat(snapshot.content()).hasSize(ExerciseGenerationFileSnapshotDTO.MAX_CONTENT_BYTES);
        assertThat(snapshot.bytes()).isEqualTo(fullContent.getBytes(StandardCharsets.UTF_8).length);
        assertThat(snapshot.sha256()).isEqualTo(sha256Hex(fullContent)).isNotEqualTo(sha256Hex(snapshot.content()));
    }

    @Test
    void fileSnapshot_truncatesMultibyteContentOnACodePointBoundaryWithinTheByteCap() {
        // MAX_CONTENT_BYTES is a BYTE budget. A naive byte-offset cut can split a multi-byte code point; decoding that head with the default REPLACE action would substitute a
        // 3-byte U+FFFD for the 1-2 lost bytes, pushing the re-encoded content BACK OVER the cap and injecting a character absent from the original. Truncation must land on a
        // code-point boundary: valid UTF-8, no injected replacement char, and strictly within the byte cap.
        String head = "a".repeat(ExerciseGenerationFileSnapshotDTO.MAX_CONTENT_BYTES - 2);
        String fullContent = head + "😀".repeat(100); // a 4-byte emoji straddles the byte cap (its lead byte sits at MAX_CONTENT_BYTES - 2)

        ExerciseGenerationFileSnapshotDTO snapshot = ExerciseGenerationFileSnapshotDTO.of("solution/E.java", ExerciseGenerationFileSnapshotDTO.ACTION_EDIT, fullContent, 0);

        assertThat(snapshot.truncated()).isTrue();
        byte[] contentBytes = snapshot.content().getBytes(StandardCharsets.UTF_8);
        // Within the byte cap: no byte-budget overflow from an injected replacement character.
        assertThat(contentBytes.length).isLessThanOrEqualTo(ExerciseGenerationFileSnapshotDTO.MAX_CONTENT_BYTES);
        // A faithful, valid-UTF-8 prefix of the original: no U+FFFD, no mid-codepoint split (re-encoding round-trips byte-identically).
        assertThat(snapshot.content()).doesNotContain("\uFFFD").isEqualTo(fullContent.substring(0, snapshot.content().length()));
        assertThat(new String(contentBytes, StandardCharsets.UTF_8)).isEqualTo(snapshot.content());
        // The whole-file hash/size still describe the untruncated content so a client can detect change despite truncation.
        assertThat(snapshot.bytes()).isEqualTo(fullContent.getBytes(StandardCharsets.UTF_8).length);
        assertThat(snapshot.sha256()).isEqualTo(sha256Hex(fullContent));
    }

    @Test
    void fileSnapshot_keepsAWholeCodePointThatEndsExactlyAtTheByteCap() {
        // A multi-byte code point whose last byte lands exactly on the cap must be kept whole (not over-trimmed): the boundary back-off only trims a straddling code point.
        String head = "a".repeat(ExerciseGenerationFileSnapshotDTO.MAX_CONTENT_BYTES - 4);
        String fullContent = head + "😀".repeat(100); // first emoji occupies bytes [MAX-4, MAX): it ends exactly at the cap

        ExerciseGenerationFileSnapshotDTO snapshot = ExerciseGenerationFileSnapshotDTO.of("solution/E.java", ExerciseGenerationFileSnapshotDTO.ACTION_EDIT, fullContent, 0);

        byte[] contentBytes = snapshot.content().getBytes(StandardCharsets.UTF_8);
        assertThat(contentBytes).hasSize(ExerciseGenerationFileSnapshotDTO.MAX_CONTENT_BYTES);
        assertThat(snapshot.content()).doesNotContain("\uFFFD").endsWith("😀");
    }

    @Test
    void fileSnapshot_serialisesWithTheFileSnapshotDiscriminatorSoItIsToldApartOnTheSharedTopic() throws Exception {
        ExerciseGenerationFileSnapshotDTO snapshot = ExerciseGenerationFileSnapshotDTO.of("solution/A.java", ExerciseGenerationFileSnapshotDTO.ACTION_CREATE, "x", 3);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(snapshot));

        // The fixed discriminator is what lets the client distinguish a snapshot from a progress event on the shared per-user topic.
        assertThat(json.get("type").asText()).isEqualTo("FILE_SNAPSHOT");
        assertThat(json.get("repo").asText()).isEqualTo("solution");
        assertThat(json.get("action").asText()).isEqualTo("create");
    }

    // ---- ExerciseGenerationEventDTO --------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void event_ofFactory_leavesTerminalOnlyFieldsUnset() {
        ExerciseGenerationEventDTO event = ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, "working");

        assertThat(event.type()).isEqualTo(ExerciseGenerationEventDTO.Type.PROGRESS);
        assertThat(event.message()).isEqualTo("working");
        assertThat(event.completionStatus()).isNull();
        assertThat(event.verdict()).isNull();
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void event_doneFactory_carriesCompletionStatusAndVerdict() {
        ExerciseGenerationVerdictDTO verdict = new ExerciseGenerationVerdictDTO(true, true, true, 3, List.of());

        ExerciseGenerationEventDTO event = ExerciseGenerationEventDTO.done("saved", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, verdict);

        assertThat(event.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(event.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.SUCCESS);
        assertThat(event.verdict()).isEqualTo(verdict);
    }

    @Test
    void event_progressEvent_omitsNullTerminalFieldsAndSerialisesEnumAsName() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "go")));

        assertThat(json.get("type").asText()).isEqualTo("STARTED");
        assertThat(json.get("message").asText()).isEqualTo("go");
        // NON_EMPTY: the fields only a terminal DONE carries must not appear on a non-terminal event.
        assertThat(json.has("completionStatus")).isFalse();
        assertThat(json.has("verdict")).isFalse();
    }

    // ---- ExerciseGenerationVerdictDTO ------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void verdict_omitsReasons_whenEmpty_butIncludesThemWhenPresent() throws Exception {
        JsonNode accepted = mapper.readTree(mapper.writeValueAsString(new ExerciseGenerationVerdictDTO(true, true, true, 5, List.of())));
        // NON_EMPTY: an accepted verdict has no reasons, so the array must be absent from the wire (not an empty []).
        assertThat(accepted.has("reasons")).isFalse();
        assertThat(accepted.get("accepted").asBoolean()).isTrue();
        assertThat(accepted.get("testCount").asInt()).isEqualTo(5);

        JsonNode rejected = mapper.readTree(mapper.writeValueAsString(new ExerciseGenerationVerdictDTO(false, false, true, 2, List.of("solution failed"))));
        assertThat(rejected.get("reasons")).hasSize(1);
        assertThat(rejected.get("reasons").get(0).asText()).isEqualTo("solution failed");
    }
}

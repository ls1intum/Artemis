package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.atlas.api.CompetencyRelationApi;
import de.tum.cit.aet.artemis.lecture.api.LectureTranscriptionsRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitApi;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

class HyperionQuizQuestionPoolMaterialServiceTest {

    @Mock
    private CompetencyRelationApi competencyRelationApi;

    @Mock
    private LectureUnitApi lectureUnitApi;

    @Mock
    private LectureTranscriptionsRepositoryApi transcriptionsApi;

    private HyperionQuizQuestionPoolMaterialService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new HyperionQuizQuestionPoolMaterialService(Optional.of(competencyRelationApi), Optional.of(lectureUnitApi), Optional.of(transcriptionsApi), 60000);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void constructor_rejectsAnEmptyMaterialBudget() {
        assertThatThrownBy(() -> new HyperionQuizQuestionPoolMaterialService(Optional.empty(), Optional.empty(), Optional.empty(), 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-material-characters");
    }

    @Test
    void findGroundableUnits_keepsOnlyUnitsWithMaterialInIdOrderPerCompetency() {
        TextUnit withContent = textUnit(3L, "Arrays hold elements of one type.");
        TextUnit blank = textUnit(4L, " ");
        OnlineUnit online = new OnlineUnit();
        online.setId(5L);
        AttachmentVideoUnit videoOnly = new AttachmentVideoUnit();
        videoOnly.setId(6L);
        AttachmentVideoUnit withTranscript = new AttachmentVideoUnit();
        withTranscript.setId(1L);
        when(competencyRelationApi.findLectureUnitIdsPerCompetency(Set.of(9L, 8L, 7L))).thenReturn(Map.of(9L, Set.of(3L, 4L, 5L, 6L, 1L), 8L, Set.of(4L)));
        when(lectureUnitApi.findAllByIds(anyCollection())).thenReturn(List.of(withContent, blank, online, videoOnly, withTranscript));
        when(transcriptionsApi.findByLectureUnit_Id(6L)).thenReturn(Optional.empty());
        when(transcriptionsApi.findByLectureUnit_Id(1L)).thenReturn(Optional.of(transcription(withTranscript, "Welcome back.")));

        Map<Long, List<LectureUnit>> units = service.findGroundableUnits(List.of(9L, 8L, 7L));

        assertThat(units.keySet()).containsExactly(9L);
        assertThat(units.get(9L)).extracting(LectureUnit::getId).containsExactly(1L, 3L);
    }

    @Test
    void findGroundableUnits_isEmptyWithoutCompetencies() {
        assertThat(service.findGroundableUnits(List.of())).isEmpty();
    }

    @Test
    void extractText_readsATextUnitAndStripsSurroundingWhitespace() {
        assertThat(service.extractText(textUnit(1L, "  Loops repeat a block.\n "))).isEqualTo("Loops repeat a block.");
        assertThat(service.extractText(textUnit(2L, null))).isEmpty();
    }

    @Test
    void extractText_joinsTheTranscriptSegmentsOfAVideoUnit() {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setId(2L);
        Attachment attachment = new Attachment();
        attachment.setLink("https://example.org/slides.pdf");
        unit.setAttachment(attachment);
        when(transcriptionsApi.findByLectureUnit_Id(2L)).thenReturn(Optional.of(transcription(unit, "First sentence.", "Second sentence.")));

        assertThat(service.isGroundable(unit)).isTrue();
        assertThat(service.extractText(unit)).isEqualTo("First sentence.\nSecond sentence.");
    }

    @Test
    void extractText_isEmptyForAVideoUnitWithoutPdfOrTranscript() {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setId(2L);
        when(transcriptionsApi.findByLectureUnit_Id(2L)).thenReturn(Optional.empty());

        assertThat(service.isGroundable(unit)).isFalse();
        assertThat(service.extractText(unit)).isEmpty();
        assertThat(service.extractText(new OnlineUnit())).isEmpty();
    }

    @Test
    void extractText_cutsTheTextToTheBudget() {
        HyperionQuizQuestionPoolMaterialService small = new HyperionQuizQuestionPoolMaterialService(Optional.empty(), Optional.empty(), Optional.empty(), 5);

        assertThat(small.extractText(textUnit(1L, "abcdefgh"))).isEqualTo("abcde");
        assertThat(HyperionQuizQuestionPoolMaterialService.truncate("abc", 5)).isEqualTo("abc");
    }

    @Test
    void readPdfText_extractsThePageTextInOrder(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("slides.pdf");
        try (PDDocument document = new PDDocument()) {
            addPage(document, "Weak duality bounds the primal.");
            addPage(document, "Strong duality closes the gap.");
            document.save(file.toFile());
        }

        String text = HyperionQuizQuestionPoolMaterialService.readPdfText(file);

        assertThat(text).contains("Weak duality bounds the primal.").contains("Strong duality closes the gap.");
        assertThat(text.indexOf("Weak duality")).isLessThan(text.indexOf("Strong duality"));
    }

    @Test
    void readPdfText_failsOnAFileThatIsNotAPdf(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("slides.pdf");
        FileUtils.writeStringToFile(file.toFile(), "not a pdf", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> HyperionQuizQuestionPoolMaterialService.readPdfText(file)).isInstanceOf(IOException.class);
    }

    private static void addPage(PDDocument document, String line) throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(50, 700);
            content.showText(line);
            content.endText();
        }
    }

    private static TextUnit textUnit(long id, String content) {
        TextUnit unit = new TextUnit();
        unit.setId(id);
        unit.setName("Unit " + id);
        unit.setContent(content);
        return unit;
    }

    private static LectureTranscription transcription(LectureUnit unit, String... sentences) {
        List<LectureTranscriptionSegment> segments = new ArrayList<>();
        for (int index = 0; index < sentences.length; index++) {
            segments.add(new LectureTranscriptionSegment((double) index, index + 1.0, sentences[index], 1));
        }
        return new LectureTranscription("en", segments, unit);
    }
}

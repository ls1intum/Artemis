package de.tum.cit.aet.artemis.atlas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyExerciseLinkDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyExerciseLinkResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyLectureUnitLinkResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.CourseCompetencyDTO;
import de.tum.cit.aet.artemis.atlas.dto.CourseCompetencyResponseDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

class CompetencyProvenanceDtoTest {

    private static Competency competency(boolean generatedByAi) {
        Competency competency = new Competency("Sorting", "Understand sorting.", null, CourseCompetency.DEFAULT_MASTERY_THRESHOLD, CompetencyTaxonomy.UNDERSTAND, false);
        competency.setId(1L);
        competency.setGeneratedByAi(generatedByAi);
        Course course = new Course();
        course.setId(2L);
        competency.setCourse(course);
        return competency;
    }

    @Test
    void exerciseLinkResponsesCarryProvenance() {
        CompetencyExerciseLink link = new CompetencyExerciseLink(competency(false), null, 1.0);
        link.setGeneratedByAi(true);

        assertThat(CompetencyExerciseLinkResponseDTO.of(link)).isNotNull().satisfies(dto -> assertThat(dto.generatedByAi()).isTrue());
        assertThat(CompetencyExerciseLinkDTO.of(link).generatedByAi()).isTrue();
    }

    @Test
    void lectureUnitLinkResponsesCarryProvenance() {
        TextUnit textUnit = new TextUnit();
        textUnit.setId(3L);
        textUnit.setName("Sorting basics");
        textUnit.setLecture(new Lecture());

        CompetencyLectureUnitLink link = new CompetencyLectureUnitLink(competency(false), textUnit, 1.0);
        link.setGeneratedByAi(true);

        assertThat(CompetencyLectureUnitLinkResponseDTO.of(link)).isNotNull().satisfies(dto -> assertThat(dto.generatedByAi()).isTrue());
        assertThat(de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO.of(link).generatedByAi()).isTrue();
    }

    @Test
    void courseCompetencyResponsesCarryBothProvenanceValues() {
        assertThat(CourseCompetencyResponseDTO.of(competency(true)).generatedByAi()).isTrue();
        assertThat(CourseCompetencyResponseDTO.of(competency(false)).generatedByAi()).isFalse();
        assertThat(CourseCompetencyDTO.of(competency(true)).generatedByAi()).isTrue();
        assertThat(CompetencyImportResponseDTO.of(competency(true)).generatedByAi()).isTrue();
    }

    @Test
    void courseCompetencyResponseSerializesProvenance() {
        JsonNode response = new ObjectMapper().valueToTree(CourseCompetencyResponseDTO.of(competency(true)));

        assertThat(response.path("generatedByAi").asBoolean()).isTrue();
    }
}

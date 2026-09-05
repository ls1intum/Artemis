package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.atlas.api.CompetencyRelationApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyRepositoryApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@ExtendWith(MockitoExtension.class)
class CompetencyExerciseLinkProvenanceTest {

    private static final long COURSE_ID = 7L;

    @Mock
    private CompetencyRepositoryApi competencyRepositoryApi;

    @Mock
    private CompetencyRelationApi competencyRelationApi;

    private CompetencyExerciseLinkService service;

    private Course course;

    private TextExercise exercise;

    @BeforeEach
    void setUp() {
        service = new CompetencyExerciseLinkService(Optional.of(competencyRepositoryApi), Optional.of(competencyRelationApi));
        course = new Course();
        course.setId(COURSE_ID);
        exercise = new TextExercise();
        exercise.setId(11L);
        exercise.setCourse(course);
    }

    private CourseCompetency competency(long id, String title) {
        Competency competency = new Competency(title, "Desc", null, CourseCompetency.DEFAULT_MASTERY_THRESHOLD, CompetencyTaxonomy.APPLY, false);
        competency.setId(id);
        competency.setCourse(course);
        return competency;
    }

    @Test
    void reweightingPreservesAiAuthorship() {
        CourseCompetency competency = competency(5L, "Sorting");
        CompetencyExerciseLink aiLink = new CompetencyExerciseLink(competency, exercise, 1.0);
        aiLink.setGeneratedByAi(true);
        exercise.setCompetencyLinks(new HashSet<>(Set.of(aiLink)));

        CompetencyLinksHolderDTO dto = () -> Set.of(new CompetencyLinkDTO(new CompetencyDTO(5L, "Sorting"), 0.5));
        service.updateCompetencyLinks(dto, exercise);

        assertThat(exercise.getCompetencyLinks()).singleElement().satisfies(link -> {
            assertThat(link.getWeight()).isEqualTo(0.5);
            assertThat(link.isGeneratedByAi()).isTrue();
        });
    }

    @Test
    void manuallyAddedLinkDefaultsToManualAuthorship() {
        CourseCompetency added = competency(6L, "Recursion");
        exercise.setCompetencyLinks(new HashSet<>());
        when(competencyRepositoryApi.findCompetencyOrPrerequisiteByIdElseThrow(6L)).thenReturn(added);

        CompetencyLinksHolderDTO dto = () -> Set.of(new CompetencyLinkDTO(new CompetencyDTO(6L, "Recursion"), 1.0));
        service.updateCompetencyLinks(dto, exercise);

        assertThat(exercise.getCompetencyLinks()).singleElement().satisfies(link -> assertThat(link.isGeneratedByAi()).isFalse());
    }

    @Test
    void creationCopyPreservesAiAuthorship() {
        CourseCompetency competency = competency(5L, "Sorting");
        CompetencyExerciseLink aiLink = new CompetencyExerciseLink(competency, exercise, 1.0);
        aiLink.setGeneratedByAi(true);
        when(competencyRepositoryApi.findCompetencyOrPrerequisiteByIdElseThrow(5L)).thenReturn(competency);

        service.addCompetencyLinksForCreation(exercise, new HashSet<>(Set.of(aiLink)));

        assertThat(exercise.getCompetencyLinks()).singleElement().satisfies(link -> assertThat(link.isGeneratedByAi()).isTrue());
    }
}

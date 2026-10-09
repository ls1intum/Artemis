package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.ExpectedSets;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.PresentSets;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

class IngestionBrowserGapServiceBehaviourTest {

    private static final long COURSE_ID = 42L;

    @Test
    void preservesAMissingUnitWhenTheOptionalLectureModuleCannotResolveItsDeletedParent() {
        IngestionBrowserGapService service = gapService(false);
        ExpectedSets expected = new ExpectedSets(Map.of(), Map.of(), Map.of(COURSE_ID, Set.of(7L)), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        PresentSets present = new PresentSets(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

        List<MissingEntityDTO> missing = service.missingEntities(COURSE_ID, expected, present);

        assertThat(missing).filteredOn(entity -> entity.type().equals(SearchableEntitySchema.TypeValues.LECTURE_UNIT)).singleElement().satisfies(entity -> {
            assertThat(entity.entityId()).isEqualTo(7L);
            assertThat(entity.title()).isNull();
            assertThat(entity.lectureId()).isNull();
        });
    }

    @Test
    void doesNotReportIrisContentGapsWhenIrisIsDisabled() {
        IngestionBrowserGapService service = gapService(false);
        ExpectedSets expected = new ExpectedSets(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(COURSE_ID, Set.of(7L)), Map.of(COURSE_ID, Set.of(8L)));
        PresentSets present = new PresentSets(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

        assertThat(service.contentGaps(COURSE_ID, expected, present)).isEmpty();
    }

    private static IngestionBrowserGapService gapService(boolean irisEnabled) {
        MockEnvironment environment = new MockEnvironment().withProperty("artemis.iris.enabled", Boolean.toString(irisEnabled));
        return new IngestionBrowserGapService(mock(ExerciseRepository.class), mock(FaqRepository.class), mock(ChannelRepository.class), mock(CourseRepository.class),
                Optional.empty(), Optional.<LectureUnitRepositoryApi>empty(), Optional.<ExamRepositoryApi>empty(), environment);
    }
}

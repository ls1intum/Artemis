package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.CourseIndexDriftDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;

@ExtendWith(MockitoExtension.class)
class CourseIndexDriftServiceTest {

    @Mock
    private SearchableEntityCountService countService;

    @Mock
    private LectureRepository lectureRepository;

    @Mock
    private ExamRepository examRepository;

    @Mock
    private FaqRepository faqRepository;

    @InjectMocks
    private CourseIndexDriftService driftService;

    @Test
    void assemblesPresentFromWeaviateAndExpectedFromDatabase() {
        long courseId = 42L;
        when(countService.countIndexed(eq(courseId), any())).thenReturn(3L);
        when(lectureRepository.findLectureIdsByCourseId(courseId)).thenReturn(Set.of(1L, 2L));
        when(examRepository.findByCourseId(courseId)).thenReturn(List.of(new Exam()));
        when(faqRepository.findAllByCourseId(courseId)).thenReturn(List.of(new Faq(), new Faq(), new Faq(), new Faq()));

        CourseIndexDriftDTO drift = driftService.getDrift(courseId);

        assertThat(drift.courseId()).isEqualTo(courseId);
        // present is read from Weaviate for every type
        assertThat(drift.types()).isNotEmpty().allSatisfy(type -> assertThat(type.present()).isEqualTo(3L));
        // expected is computed for the types with a clean per-course source, and null otherwise
        assertThat(expectedFor(drift, SearchableEntitySchema.TypeValues.LECTURE)).isEqualTo(2L);
        assertThat(expectedFor(drift, SearchableEntitySchema.TypeValues.EXAM)).isEqualTo(1L);
        assertThat(expectedFor(drift, SearchableEntitySchema.TypeValues.FAQ)).isEqualTo(4L);
        assertThat(expectedFor(drift, SearchableEntitySchema.TypeValues.COURSE)).isEqualTo(1L);
        assertThat(expectedFor(drift, SearchableEntitySchema.TypeValues.EXERCISE)).isNull();
        assertThat(expectedFor(drift, SearchableEntitySchema.TypeValues.LECTURE_UNIT)).isNull();
    }

    private Long expectedFor(CourseIndexDriftDTO drift, String type) {
        return drift.types().stream().filter(entry -> entry.type().equals(type)).findFirst().orElseThrow().expected();
    }
}

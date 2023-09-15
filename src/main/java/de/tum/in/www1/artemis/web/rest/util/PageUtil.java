package de.tum.in.www1.artemis.web.rest.util;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;

public class PageUtil {

    @NotNull
    public static PageRequest createExercisePageRequest(PageableSearchDTO<String> search) {
        var sortOptions = Sort.by(Exercise.ExerciseSearchColumn.valueOf(search.getSortedColumn()).getMappedColumnName());
        sortOptions = search.getSortingOrder() == SortingOrder.ASCENDING ? sortOptions.ascending() : sortOptions.descending();
        return PageRequest.of(search.getPage() - 1, search.getPageSize(), sortOptions);
    }

    @NotNull
    public static PageRequest createLecturePageRequest(PageableSearchDTO<String> search) {
        var sortOptions = Sort.by(Lecture.LectureSearchColumn.valueOf(search.getSortedColumn()).getMappedColumnName());
        sortOptions = search.getSortingOrder() == SortingOrder.ASCENDING ? sortOptions.ascending() : sortOptions.descending();
        return PageRequest.of(search.getPage() - 1, search.getPageSize(), sortOptions);
    }

    @NotNull
    public static PageRequest createCompetencyPageRequest(PageableSearchDTO<String> search) {
        var sortOptions = Sort.by(Competency.CompetencySearchColumn.valueOf(search.getSortedColumn()).getMappedColumnName());
        sortOptions = search.getSortingOrder() == SortingOrder.ASCENDING ? sortOptions.ascending() : sortOptions.descending();
        return PageRequest.of(search.getPage() - 1, search.getPageSize(), sortOptions);
    }

    @NotNull
    public static PageRequest createLearningPathPageRequest(PageableSearchDTO<String> search) {
        var sortOptions = Sort.by(LearningPath.LearningPathSearchColumn.valueOf(search.getSortedColumn()).getMappedColumnName());
        sortOptions = search.getSortingOrder() == SortingOrder.ASCENDING ? sortOptions.ascending() : sortOptions.descending();
        return PageRequest.of(search.getPage() - 1, search.getPageSize(), sortOptions);
    }

    @NotNull
    public static PageRequest createExamPageRequest(PageableSearchDTO<String> search) {
        var sortOptions = Sort.by(Exam.ExamSearchColumn.valueOf(search.getSortedColumn()).getMappedColumnName());
        sortOptions = search.getSortingOrder() == SortingOrder.ASCENDING ? sortOptions.ascending() : sortOptions.descending();
        return PageRequest.of(search.getPage() - 1, search.getPageSize(), sortOptions);
    }

    @NotNull
    public static PageRequest createGradingScaleRequest(PageableSearchDTO<String> search) {
        var sortOptions = Sort.by(GradingScale.GradingScaleSearchColumn.valueOf(search.getSortedColumn()).getMappedColumnName());
        sortOptions = search.getSortingOrder() == SortingOrder.ASCENDING ? sortOptions.ascending() : sortOptions.descending();
        return PageRequest.of(search.getPage() - 1, search.getPageSize(), sortOptions);
    }
}

package de.tum.in.www1.artemis.lecture.web.rest.util;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public class PageUtil {

    @NotNull
    public static PageRequest createLecturePageRequest(PageableSearchDTO<String> search) {
        var sortOptions = Sort.by(Lecture.LectureSearchColumn.valueOf(search.getSortedColumn()).getMappedColumnName());
        sortOptions = search.getSortingOrder() == SortingOrder.ASCENDING ? sortOptions.ascending() : sortOptions.descending();
        return PageRequest.of(search.getPage() - 1, search.getPageSize(), sortOptions);
    }
}

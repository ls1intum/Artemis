package de.tum.in.www1.artemis.web.rest.util;

import java.util.function.Function;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;

public class PageUtil {

    @NotNull
    public static PageRequest createDefaultPageRequest(PageableSearchDTO<String> search, Function<String, String> getMappedColumnName) {
        var sortOptions = Sort.by(getMappedColumnName.apply(search.getSortedColumn()));
        sortOptions = search.getSortingOrder() == SortingOrder.ASCENDING ? sortOptions.ascending() : sortOptions.descending();
        return PageRequest.of(search.getPage() - 1, search.getPageSize(), sortOptions);
    }
}

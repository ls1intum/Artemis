package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageRequest;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * A search that does not state a valid page, page size or sorted column is a malformed request, which is answered as such instead of failing inside the page request.
 */
class PageUtilTest {

    private static SearchTermPageableSearchDTO<String> search(int page, int pageSize, String sortedColumn) {
        var search = new SearchTermPageableSearchDTO<String>();
        search.setPage(page);
        search.setPageSize(pageSize);
        search.setSortedColumn(sortedColumn);
        search.setSortingOrder(SortingOrder.ASCENDING);
        return search;
    }

    @Test
    void shouldCreateThePageRequestOfAValidSearch() {
        PageRequest request = PageUtil.createDefaultPageRequest(search(2, 10, "TITLE"), PageUtil.ColumnMapping.LECTURE);

        assertThat(request.getPageNumber()).isEqualTo(1);
        assertThat(request.getPageSize()).isEqualTo(10);
        assertThat(request.getSort().getOrderFor("title")).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({ "0, 10", "-1, 10", "1, 0", "1, -5", "0, 0" })
    void shouldRejectAPageOrPageSizeBelowOne(int page, int pageSize) {
        assertThatThrownBy(() -> PageUtil.createDefaultPageRequest(search(page, pageSize, "TITLE"), PageUtil.ColumnMapping.LECTURE)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void shouldRejectAMissingSortedColumn() {
        assertThatThrownBy(() -> PageUtil.createDefaultPageRequest(search(1, 10, null), PageUtil.ColumnMapping.LECTURE)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void shouldRejectAnUnknownSortedColumn() {
        assertThatThrownBy(() -> PageUtil.createDefaultPageRequest(search(1, 10, "PASSWORD"), PageUtil.ColumnMapping.LECTURE)).isInstanceOf(BadRequestAlertException.class);
    }
}

package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.communication.domain.PostSortCriterion;
import de.tum.cit.aet.artemis.communication.dto.PostContextFilterDTO;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;

class PostContextFilterDTOTest {

    private static PostContextFilterDTO create(long[] conversationIds, String searchText) {
        return new PostContextFilterDTO(1L, 2L, conversationIds, new long[] { 5L }, searchText, true, false, true, PostSortCriterion.CREATION_DATE, SortingOrder.ASCENDING, true,
                true);
    }

    @Test
    void shortConstructorDefaultsPinnedOnlyAndUnverifiedIrisToFalse() {
        var dto = new PostContextFilterDTO(1L, null, null, null, "text", null, null, null, PostSortCriterion.CREATION_DATE, SortingOrder.ASCENDING);
        assertThat(dto.pinnedOnly()).isFalse();
        assertThat(dto.filterToUnverifiedIris()).isFalse();
    }

    @Test
    void mediumConstructorKeepsPinnedOnlyAndDefaultsUnverifiedIrisToFalse() {
        var dto = new PostContextFilterDTO(1L, null, null, null, "text", null, null, null, PostSortCriterion.CREATION_DATE, SortingOrder.ASCENDING, true);
        assertThat(dto.pinnedOnly()).isTrue();
        assertThat(dto.filterToUnverifiedIris()).isFalse();
    }

    @Test
    void equalsComparesArraysByContent() {
        var dto = create(new long[] { 1L, 2L }, "a");
        assertThat(dto).isEqualTo(dto);
        assertThat(dto).isEqualTo(create(new long[] { 1L, 2L }, "a"));
        assertThat(dto).hasSameHashCodeAs(create(new long[] { 1L, 2L }, "a"));
        assertThat(dto).isNotEqualTo(create(new long[] { 1L, 3L }, "a"));
        assertThat(dto).isNotEqualTo(create(new long[] { 1L, 2L }, "b"));
        assertThat(dto).isNotEqualTo(null);
        assertThat(dto).isNotEqualTo("not a dto");
    }

    @Test
    void equalsDetectsDifferentScalarFields() {
        var dto = create(new long[] { 1L }, "a");
        var differentPinned = new PostContextFilterDTO(1L, 2L, new long[] { 1L }, new long[] { 5L }, "a", true, false, true, PostSortCriterion.CREATION_DATE,
                SortingOrder.ASCENDING, false, true);
        var differentIris = new PostContextFilterDTO(1L, 2L, new long[] { 1L }, new long[] { 5L }, "a", true, false, true, PostSortCriterion.CREATION_DATE, SortingOrder.ASCENDING,
                true, false);
        var differentOrder = new PostContextFilterDTO(1L, 2L, new long[] { 1L }, new long[] { 5L }, "a", true, false, true, PostSortCriterion.CREATION_DATE,
                SortingOrder.DESCENDING, true, true);
        var differentAuthors = new PostContextFilterDTO(1L, 2L, new long[] { 1L }, new long[] { 6L }, "a", true, false, true, PostSortCriterion.CREATION_DATE,
                SortingOrder.ASCENDING, true, true);
        assertThat(dto).isNotEqualTo(differentPinned).isNotEqualTo(differentIris).isNotEqualTo(differentOrder).isNotEqualTo(differentAuthors);
    }

    @Test
    void toStringRendersArraysAndAllFields() {
        String text = create(new long[] { 1L, 2L }, "a").toString();
        assertThat(text).contains("courseId=1", "plagiarismCaseId=2", "conversationIds=[1, 2]", "authorIds=[5]", "searchText=a", "pinnedOnly=true", "filterToUnverifiedIris=true");
    }
}

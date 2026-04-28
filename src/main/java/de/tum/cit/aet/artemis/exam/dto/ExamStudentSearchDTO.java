package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExamStudentSearchDTO(int page, int pageSize, SortingOrder sortingOrder, String sortedColumn, String searchTerm, String filterProp) {
}

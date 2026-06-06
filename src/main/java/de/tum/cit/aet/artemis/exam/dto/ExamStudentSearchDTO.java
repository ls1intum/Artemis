package de.tum.cit.aet.artemis.exam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExamStudentSearchDTO(@Min(0) int page, @Min(1) @Max(200) int pageSize, SortingOrder sortingOrder, String sortedColumn, String searchTerm, String filterProp) {
}

package de.tum.cit.aet.artemis.core.util;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.PageableSearchDTO;

public class PageUtil {

    /**
     * Enum of column name maps for different entities. Maps column (client) to attribute (persistence entity) <br/>
     * Each map contains only the columns for which we want to allow a pageable search.
     */
    public enum ColumnMapping {

        // @formatter:off
        COMPETENCY(Map.of(
            "ID", "id",
            "TITLE", "title",
            "COURSE_TITLE", "course.title",
            "SEMESTER", "course.semester"
        )),
        COURSE(Map.of(
            "ID", "id",
            "TITLE", "title",
            "SHORT_NAME", "shortName",
            "SEMESTER", "semester"
        )),
        EXAM(Map.of(
            "ID", "id",
            "TITLE", "title",
            "COURSE_TITLE", "course.title",
            "EXAM_MODE", "testExam"
        )),
        EXERCISE(Map.of(
            "ID", "id",
            "TITLE", "title",
            "PROGRAMMING_LANGUAGE", "programmingLanguage",
            "COURSE_TITLE", "course.title",
            "EXAM_TITLE", "exerciseGroup.exam.title"
        )),
        GRADING_SCALE(Map.of(
            "ID", "id",
            "COURSE_TITLE", "course.title",
            "EXAM_TITLE", "exam.title"
        )),
        LEARNING_PATH(Map.of(
            "ID", "id",
            "USER_LOGIN", "user.login",
            "USER_NAME", "user.lastName",
            "PROGRESS", "progress"
        )),
        LECTURE(Map.of(
            "ID", "id",
            "TITLE", "title",
            "COURSE_TITLE", "course.title",
            "SEMESTER", "course.semester"
        )),
        STUDENT_PARTICIPATION(Map.of(
            "ID", "id",
            "STUDENT_NAME", "student.firstName"
        )),
        BUILD_JOB(Map.of(
            "id", "id",
            "name", "name",
            "build_completion_date", "buildCompletionDate"
        )),
        FEEDBACK_ANALYSIS(Map.of(
            "count", "COUNT(f.id)",
            "detailText", "f.detailText",
            "testCaseName", "f.testCase.testName"
        ));
        // @formatter:on

        private final Map<String, String> columnNameMap;

        ColumnMapping(Map<String, String> columnNameMap) {
            this.columnNameMap = columnNameMap;
        }

        public Map<String, String> getColumnNameMap() {
            return columnNameMap;
        }

        public String getMappedColumnName(String columnName) {
            return columnNameMap.get(columnName);
        }
    }

    /**
     * Creates a default {@link PageRequest} based on the provided {@link PageableSearchDTO} and {@link ColumnMapping}.
     * This method maps the sorted column name from the provided search DTO using the column mapping,
     * applies the appropriate sorting order (ascending or descending), and constructs a {@link PageRequest}
     * with pagination and sorting information.
     *
     * <p>
     * If the mapped column name contains a "COUNT(" expression, this method treats it as an unsafe sort expression
     * and uses {@link JpaSort(String)} to apply sorting directly to the database column.
     * </p>
     *
     * @param search        The {@link PageableSearchDTO} containing pagination and sorting parameters (e.g., page number, page size, sorted column, and sorting order).
     * @param columnMapping The {@link ColumnMapping} object used to map the sorted column name from the DTO to the actual database column.
     * @return A {@link PageRequest} object containing the pagination and sorting options based on the search and column mapping.
     * @throws IllegalArgumentException if any of the parameters are invalid or missing.
     * @throws NullPointerException     if the search or columnMapping parameters are null.
     */
    @NotNull
    public static PageRequest createDefaultPageRequest(PageableSearchDTO<String> search, ColumnMapping columnMapping) {
        String mappedColumn = columnMapping.getMappedColumnName(search.getSortedColumn());

        var sortOptions = mappedColumn.contains("COUNT(") ? JpaSort.unsafe(mappedColumn) : Sort.by(mappedColumn);

        sortOptions = search.getSortingOrder() == SortingOrder.ASCENDING ? sortOptions.ascending() : sortOptions.descending();
        return PageRequest.of(search.getPage() - 1, search.getPageSize(), sortOptions);
    }
}

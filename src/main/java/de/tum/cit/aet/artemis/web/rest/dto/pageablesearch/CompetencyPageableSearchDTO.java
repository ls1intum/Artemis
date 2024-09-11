package de.tum.cit.aet.artemis.web.rest.dto.pageablesearch;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.web.rest.dto.SearchResultPageDTO;

/**
 * Search DTO for a list of competencies matching the given search terms. The result should be paged,
 * meaning that it only contains a predefined number of elements in order to not fetch and return too many.
 *
 * @see SearchResultPageDTO
 */
// TODO: convert to Record, use composition for common attributes
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CompetencyPageableSearchDTO extends PageableSearchDTO<String> {

    /**
     * The search terms
     */

    private String title;

    private String description;

    private String semester;

    private String courseTitle;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }
}

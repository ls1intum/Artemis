package de.tum.in.www1.artemis.web.rest.dto;

// Using an interface here allows us to directly return the DTO from the database
public interface CourseManagementOverviewDetailsDTO {

    Long getId();

    String getTitle();

    Boolean getTestCourse();

    String getSemester();

    String getShortName();

    String getColor();

    String getStudentGroupName();

    String getTeachingAssistantGroupName();

    String getInstructorGroupName();
}

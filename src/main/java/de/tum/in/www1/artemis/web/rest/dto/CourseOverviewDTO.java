package de.tum.in.www1.artemis.web.rest.dto;

public class CourseOverviewDTO {

    private Long id;

    private String title;

    private Boolean testCourse;

    private String semester;

    private String shortName;

    private String color;

    private Long numberOfStudents;

    private Long numberOfTeachingAssistants;

    private Long numberOfInstructors;

    private String studentGroupName;

    private String teachingAssistantGroupName;

    private String instructorGroupName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getTestCourse() {
        return testCourse;
    }

    public void setTestCourse(Boolean testCourse) {
        this.testCourse = testCourse;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Long getNumberOfStudents() {
        return numberOfStudents;
    }

    public void setNumberOfStudents(Long numberOfStudents) {
        this.numberOfStudents = numberOfStudents;
    }

    public Long getNumberOfTeachingAssistants() {
        return numberOfTeachingAssistants;
    }

    public void setNumberOfTeachingAssistants(Long numberOfTeachingAssistants) {
        this.numberOfTeachingAssistants = numberOfTeachingAssistants;
    }

    public Long getNumberOfInstructors() {
        return numberOfInstructors;
    }

    public void setNumberOfInstructors(Long numberOfInstructors) {
        this.numberOfInstructors = numberOfInstructors;
    }

    public String getStudentGroupName() {
        return studentGroupName;
    }

    public void setStudentGroupName(String studentGroupName) {
        this.studentGroupName = studentGroupName;
    }

    public String getTeachingAssistantGroupName() {
        return teachingAssistantGroupName;
    }

    public void setTeachingAssistantGroupName(String teachingAssistantGroupName) {
        this.teachingAssistantGroupName = teachingAssistantGroupName;
    }

    public String getInstructorGroupName() {
        return instructorGroupName;
    }

    public void setInstructorGroupName(String instructorGroupName) {
        this.instructorGroupName = instructorGroupName;
    }
}

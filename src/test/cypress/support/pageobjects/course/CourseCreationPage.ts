import dayjs from 'dayjs/esm';

import { COURSE_ADMIN_BASE, COURSE_BASE, POST, PUT } from '../../constants';
import { enterDate } from '../../utils';

/**
 * A class which encapsulates UI selectors and actions for the course creation page.
 */
export class CourseCreationPage {
    /**
     * Sets the title of the course.
     * @param title the exam title
     */
    setTitle(title: string) {
        cy.get('#field_title').clear().type(title);
    }

    /**
     * Sets the short name of the course.
     * @param shortName the course short name
     */
    setShortName(shortName: string) {
        cy.get('#field_shortName').clear().type(shortName);
    }

    /**
     * Sets the description of the course.
     * @param description the course description
     */
    setDescription(description: string) {
        cy.get('#field_description').clear().type(description);
    }

    /**
     * @param date the date when the exam starts
     */
    setStartDate(date: dayjs.Dayjs) {
        enterDate('#field_startDate', date);
    }

    /**
     * @param date the date when the exam will end
     */
    setEndDate(date: dayjs.Dayjs) {
        enterDate('#field_endDate', date);
    }

    /**
     * Sets course to be a test course
     * @param testCourse if is a test course
     */
    setTestCourse(testCourse: boolean) {
        if (testCourse) {
            cy.get('#field_testCourse').check();
        } else {
            cy.get('#field_testCourse').uncheck();
        }
    }

    /**
     * Sets semester for the course
     * @param semester the semester of the course
     */
    setSemester(semester: string) {
        cy.get('#semester').select(semester);
    }

    /**
     * Sets the maximum achievable points in the course.
     * @param courseMaxPoints the max points
     */
    setCourseMaxPoints(courseMaxPoints: number) {
        cy.get('#field_maxPoints').clear().type(courseMaxPoints.toString());
    }

    /**
     * Sets the default programming language
     * @param programmingLanguage the programming language
     */
    setProgrammingLanguage(programmingLanguage: string) {
        cy.get('#programmingLanguage').select(programmingLanguage);
    }

    /**
     * Sets if the course group names should be customized
     * @param customizeGroupNames customize the group names
     */
    setCustomizeGroupNames(customizeGroupNames: boolean) {
        if (customizeGroupNames) {
            cy.get('#field_customizeGroupNamesEnabled').check();
        } else {
            cy.get('#field_customizeGroupNamesEnabled').uncheck();
        }
    }

    /**
     * Sets the customized group name for students
     * @param groupName the group name
     */
    setStudentGroup(groupName: string) {
        cy.get('#field_studentGroupName').clear().type(groupName);
    }

    /**
     * Sets the customized group name for tutors
     * @param groupName the group name
     */
    setTutorGroup(groupName: string) {
        cy.get('#field_teachingAssistantGroupName').clear().type(groupName);
    }

    /**
     * Sets the customized group name for editors
     * @param groupName the group name
     */
    setEditorGroup(groupName: string) {
        cy.get('#field_editorGroupName').clear().type(groupName);
    }

    /**
     * Sets the customized group name for editors
     * @param groupName the group name
     */
    setInstructorGroup(groupName: string) {
        cy.get('#field_instructorGroupName').clear().type(groupName);
    }

    /**
     * Sets if complaints are enabled
     * @param complaints if complaints should be enabled
     */
    setEnableComplaints(complaints: boolean) {
        if (complaints) {
            cy.get('#field_maxComplaintSettingEnabled').check();
        } else {
            cy.get('#field_maxComplaintSettingEnabled').uncheck();
        }
    }

    /**
     * Sets maximum amount of complaints
     * @param maxComplaints the maximum complaints
     */
    setMaxComplaints(maxComplaints: number) {
        cy.get('#field_maxComplaints').clear().type(maxComplaints.toString());
    }

    /**
     * Sets maximum amount of team complaints
     * @param maxTeamComplaints the maximum team complaints
     */
    setMaxTeamComplaints(maxTeamComplaints: number) {
        cy.get('#field_maxTeamComplaints').clear().type(maxTeamComplaints.toString());
    }

    /**
     * Sets the maximal complaints time in days
     * @param maxComplaintsTimeDays the maximal complaints time in days
     */
    setMaxComplaintsTimeDays(maxComplaintsTimeDays: number) {
        cy.get('#field_maxComplaintTimeDays').clear().type(maxComplaintsTimeDays.toString());
    }

    /**
     * Sets the maximal complaint text limit
     * @param maxComplaintTextLimit the maximal complaint text limit
     */
    setMaxComplaintTextLimit(maxComplaintTextLimit: number) {
        cy.get('#field_maxComplaintTextLimit').clear().type(maxComplaintTextLimit.toString());
    }

    /**
     * Sets the maximal complaint response text limit
     * @param maxComplaintResponseTextLimit the maximal complaint response text limit
     */
    setMaxComplaintResponseTextLimit(maxComplaintResponseTextLimit: number) {
        cy.get('#field_maxComplaintResponseTextLimit').clear().type(maxComplaintResponseTextLimit.toString());
    }

    /**
     * Sets if more feedback requests are enabled
     * @param moreFeedback if more feedback should be enabled
     */
    setEnableMoreFeedback(moreFeedback: boolean) {
        if (moreFeedback) {
            cy.get('#field_maxRequestMoreFeedbackSettingEnabled').check();
        } else {
            cy.get('#field_maxRequestMoreFeedbackSettingEnabled').uncheck();
        }
    }

    /**
     * Sets the maximal request more feedback time in days
     * @param maxRequestMoreFeedbackTimeDays maximal request more feedback time in days
     */
    setMaxRequestMoreFeedbackTimeDays(maxRequestMoreFeedbackTimeDays: number) {
        cy.get('#field_maxRequestMoreFeedbackTimeDays').clear().type(maxRequestMoreFeedbackTimeDays.toString());
    }

    /**
     * Sets if course is an online course
     * @param onlineCourse if should be online course
     */
    setOnlineCourse(onlineCourse: boolean) {
        if (onlineCourse) {
            cy.get('#field_onlineCourse').check();
        } else {
            cy.get('#field_onlineCourse').uncheck();
        }
    }

    /**
     * Sets if students can register for this course
     * @param registration if registration should be possible
     */
    setRegistrationEnabled(registration: boolean) {
        if (registration) {
            cy.get('#field_registrationEnabled').check();
        } else {
            cy.get('#field_registrationEnabled').uncheck();
        }
    }

    /**
     * Submits the created exam.
     * @returns the query chainable if a test needs to access the response
     */
    submit() {
        cy.intercept(POST, COURSE_ADMIN_BASE).as('createCourseQuery');
        cy.get('#save-entity').click();
        return cy.wait('@createCourseQuery');
    }

    /**
     * Updates the created exam.
     * @returns the query chainable if a test needs to access the response
     */
    update() {
        cy.intercept(PUT, `${COURSE_BASE}/*`).as('updateCourseQuery');
        cy.get('#save-entity').click();
        return cy.wait('@updateCourseQuery');
    }
}

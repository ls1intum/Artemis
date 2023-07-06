import { Interception } from 'cypress/types/net-stubbing';
import { convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { BASE_API, PUT } from '../../support/constants';
import { courseCreation, courseManagement, courseManagementRequest, navigationBar } from '../../support/artemis';
import { dayjsToString, generateUUID, trimDate } from '../../support/utils';
import { Course } from 'app/entities/course.model';
import day from 'dayjs/esm';
import { admin, studentOne } from '../../support/users';

// Selectors
const modalDeleteButton = '#delete';

// Common primitives
const courseData = {
    title: '',
    shortName: '',
    description: 'Lore Impsum',
    startDate: day(),
    endDate: day().add(1, 'day'),
    testCourse: true,
    semester: 'SS23',
    maxPoints: 40,
    programmingLanguage: 'JAVA',
    customizeGroupNames: false,
    studentGroupName: Cypress.env('studentGroupName'),
    tutorGroupName: Cypress.env('tutorGroupName'),
    editorGroupName: Cypress.env('editorGroupName'),
    instructorGroupName: Cypress.env('instructorGroupName'),
    enableComplaints: true,
    maxComplaints: 5,
    maxTeamComplaints: 3,
    maxComplaintTimeDays: 6,
    enableMoreFeedback: true,
    maxRequestMoreFeedbackTimeDays: 4,
    onlineCourse: true,
    presentationScoreEnabled: true,
    presentationScore: 10,
};

const editedCourseData = {
    title: '',
    shortName: '',
    testCourse: false,
};

const allowGroupCustomization: boolean = Cypress.env('allowGroupCustomization');
const dateFormat = 'MMM D, YYYY HH:mm';

describe('Course management', () => {
    beforeEach(() => {
        cy.login(admin, '/');
        const uid = generateUUID();
        courseData.title = 'Cypress course' + uid;
        courseData.shortName = 'cypress' + uid;
    });

    describe('Manual student selection', () => {
        let course: Course;

        beforeEach(() => {
            courseManagementRequest.createCourse(false, courseData.title, courseData.shortName).then((response) => {
                course = convertCourseAfterMultiPart(response);
            });
        });

        it('Adds a student manually to the course', () => {
            const username = studentOne.username;
            navigationBar.openCourseManagement();
            courseManagement.openCourse(courseData.shortName);
            courseManagement.addStudentToCourse(studentOne);
            cy.get('#registered-students').contains(username).should('be.visible');
            navigationBar.openCourseManagement();
            courseManagement.openCourse(courseData.shortName);
            courseManagement.getCourseStudentGroupName().contains(`artemis-${courseData.shortName}-students (1)`);
        });

        it('Removes a student manually from the course', () => {
            const username = studentOne.username;
            courseManagementRequest.addStudentToCourse(course, studentOne);
            navigationBar.openCourseManagement();
            courseManagement.openStudentOverviewOfCourse(course.id!);
            cy.get('#registered-students').contains(username).should('be.visible');
            cy.get('#registered-students button[jhideletebutton]').should('be.visible').click();
            cy.get('.modal #delete').click();
            cy.get('#registered-students').contains(username).should('not.exist');
            navigationBar.openCourseManagement();
            courseManagement.openCourse(courseData.shortName);
            courseManagement.getCourseStudentGroupName().contains(`artemis-${courseData.shortName}-students (0)`);
        });

        after(() => {
            if (course) {
                courseManagementRequest.deleteCourse(course.id!).its('status').should('eq', 200);
            }
        });
    });

    describe('Course creation', () => {
        let courseId: number;
        let courseId2: number;

        beforeEach(() => {
            const uid = generateUUID();
            courseData.title = 'Cypress course' + uid;
            courseData.shortName = 'cypress' + uid;
        });

        it('Creates a new course', () => {
            navigationBar.openCourseManagement();
            courseManagement.openCourseCreation();
            courseCreation.setTitle(courseData.title);
            courseCreation.setShortName(courseData.shortName);
            courseCreation.setDescription(courseData.description);
            courseCreation.setTestCourse(courseData.testCourse);
            courseCreation.setStartDate(courseData.startDate);
            courseCreation.setEndDate(courseData.endDate);
            courseCreation.setSemester(courseData.semester);
            courseCreation.setCourseMaxPoints(courseData.maxPoints);
            courseCreation.setProgrammingLanguage(courseData.programmingLanguage);
            courseCreation.setEnableComplaints(courseData.enableComplaints);
            courseCreation.setMaxComplaints(courseData.maxComplaints);
            courseCreation.setMaxTeamComplaints(courseData.maxTeamComplaints);
            courseCreation.setMaxComplaintsTimeDays(courseData.maxComplaintTimeDays);
            courseCreation.setEnableMoreFeedback(courseData.enableMoreFeedback);
            courseCreation.setMaxRequestMoreFeedbackTimeDays(courseData.maxRequestMoreFeedbackTimeDays);
            courseCreation.setOnlineCourse(courseData.onlineCourse);
            courseCreation.setCustomizeGroupNames(courseData.customizeGroupNames);
            courseCreation.submit().then((request: Interception) => {
                const courseBody = request.response!.body;
                courseId = courseBody.id!;
                expect(courseBody.title).to.eq(courseData.title);
                expect(courseBody.shortName).to.eq(courseData.shortName);
                expect(courseBody.description).to.eq(courseData.description);
                expect(courseBody.testCourse).to.eq(courseData.testCourse);
                expect(trimDate(courseBody.startDate)).to.eq(trimDate(dayjsToString(courseData.startDate)));
                expect(trimDate(courseBody.endDate)).to.eq(trimDate(dayjsToString(courseData.endDate)));
                expect(courseBody.semester).to.eq(courseData.semester);
                expect(courseBody.maxPoints).to.eq(courseData.maxPoints);
                expect(courseBody.defaultProgrammingLanguage).to.eq(courseData.programmingLanguage);
                expect(courseBody.complaintsEnabled).to.eq(courseData.enableComplaints);
                expect(courseBody.maxComplaints).to.eq(courseData.maxComplaints);
                expect(courseBody.maxTeamComplaints).to.eq(courseData.maxTeamComplaints);
                expect(courseBody.maxComplaintTimeDays).to.eq(courseData.maxComplaintTimeDays);
                expect(courseBody.requestMoreFeedbackEnabled).to.eq(courseData.enableMoreFeedback);
                expect(courseBody.onlineCourse).to.eq(courseData.onlineCourse);
                expect(courseBody.studentGroupName).to.eq(`artemis-${courseData.shortName}-students`);
                expect(courseBody.editorGroupName).to.eq(`artemis-${courseData.shortName}-editors`);
                expect(courseBody.instructorGroupName).to.eq(`artemis-${courseData.shortName}-instructors`);
                expect(courseBody.teachingAssistantGroupName).to.eq(`artemis-${courseData.shortName}-tutors`);
            });
            courseManagement.getCourseHeaderTitle().contains(courseData.title).should('be.visible');
            courseManagement.getCourseHeaderDescription().contains(courseData.description);
            courseManagement.getCourseTitle().contains(courseData.title);
            courseManagement.getCourseShortName().contains(courseData.shortName);
            courseManagement.getCourseStudentGroupName().contains(`artemis-${courseData.shortName}-students (0)`);
            courseManagement.getCourseTutorGroupName().contains(`artemis-${courseData.shortName}-tutors (0)`);
            courseManagement.getCourseEditorGroupName().contains(`artemis-${courseData.shortName}-editors (0)`);
            courseManagement.getCourseInstructorGroupName().contains(`artemis-${courseData.shortName}-instructors (0)`);
            courseManagement.getCourseStartDate().contains(courseData.startDate.format(dateFormat));
            courseManagement.getCourseEndDate().contains(courseData.endDate.format(dateFormat));
            courseManagement.getCourseSemester().contains(courseData.semester);
            courseManagement.getCourseProgrammingLanguage().contains(courseData.programmingLanguage);
            courseManagement.getCourseTestCourse().contains(convertBooleanToYesNo(courseData.testCourse));
            courseManagement.getCourseOnlineCourse().contains(convertBooleanToYesNo(courseData.onlineCourse));
            courseManagement.getCourseMaxComplaints().contains(courseData.maxComplaints);
            courseManagement.getCourseMaxTeamComplaints().contains(courseData.maxTeamComplaints);
            courseManagement.getMaxComplaintTimeDays().contains(courseData.maxComplaintTimeDays);
            courseManagement.getMaxRequestMoreFeedbackTimeDays().contains(courseData.maxRequestMoreFeedbackTimeDays);
        });

        if (allowGroupCustomization) {
            it('Creates a new course with custom groups', () => {
                navigationBar.openCourseManagement();
                courseManagement.openCourseCreation();
                courseCreation.setTitle(courseData.title);
                courseCreation.setShortName(courseData.shortName);
                courseCreation.setTestCourse(courseData.testCourse);
                courseCreation.setCustomizeGroupNames(true);
                courseCreation.setStudentGroup(courseData.studentGroupName);
                courseCreation.setTutorGroup(courseData.tutorGroupName);
                courseCreation.setEditorGroup(courseData.editorGroupName);
                courseCreation.setInstructorGroup(courseData.instructorGroupName);
                courseCreation.submit().then((request: Interception) => {
                    const courseBody = request.response!.body;
                    courseId2 = courseBody.id!;
                    expect(courseBody.title).to.eq(courseData.title);
                    expect(courseBody.shortName).to.eq(courseData.shortName);
                    expect(courseBody.testCourse).to.eq(courseData.testCourse);
                    expect(courseBody.studentGroupName).to.eq(courseData.studentGroupName);
                    expect(courseBody.teachingAssistantGroupName).to.eq(courseData.tutorGroupName);
                    expect(courseBody.editorGroupName).to.eq(courseData.editorGroupName);
                    expect(courseBody.instructorGroupName).to.eq(courseData.instructorGroupName);
                });
                courseManagement.getCourseHeaderTitle().contains(courseData.title).should('be.visible');
                courseManagement.getCourseTitle().contains(courseData.title);
                courseManagement.getCourseShortName().contains(courseData.shortName);
                courseManagement.getCourseTestCourse().contains(convertBooleanToYesNo(courseData.testCourse));
                courseManagement.getCourseStudentGroupName().contains(courseData.studentGroupName);
                courseManagement.getCourseTutorGroupName().contains(courseData.tutorGroupName);
                courseManagement.getCourseEditorGroupName().contains(courseData.editorGroupName);
                courseManagement.getCourseInstructorGroupName().contains(courseData.instructorGroupName);
            });
        }

        after(() => {
            if (courseId) {
                courseManagementRequest.deleteCourse(courseId).its('status').should('eq', 200);
            }
            if (courseId2) {
                courseManagementRequest.deleteCourse(courseId2).its('status').should('eq', 200);
            }
        });
    });

    describe('Course edit', () => {
        let courseId: number;
        const uid = generateUUID();
        editedCourseData.title = 'Cypress course' + uid;
        editedCourseData.shortName = 'cypress' + uid;

        beforeEach(() => {
            courseManagementRequest.createCourse(false, courseData.title, courseData.shortName).its('status').should('eq', 201);
        });

        it('Edits a existing course', () => {
            navigationBar.openCourseManagement();
            courseManagement.openCourse(courseData.shortName);
            courseManagement.openCourseEdit();

            courseCreation.setTitle(editedCourseData.title);
            courseCreation.setTestCourse(editedCourseData.testCourse);

            courseCreation.update().then((request: Interception) => {
                const courseBody = request.response!.body;
                courseId = courseBody.id!;
                expect(courseBody.title).to.eq(editedCourseData.title);
                expect(courseBody.shortName).to.eq(courseData.shortName);
                expect(courseBody.testCourse).to.eq(editedCourseData.testCourse);
            });
            courseManagement.getCourseHeaderTitle().contains(editedCourseData.title).should('be.visible');
            courseManagement.getCourseTitle().contains(editedCourseData.title);
            courseManagement.getCourseShortName().contains(courseData.shortName);
            courseManagement.getCourseTestCourse().contains(convertBooleanToYesNo(editedCourseData.testCourse));
        });

        after(() => {
            if (courseId) {
                courseManagementRequest.deleteCourse(courseId).its('status').should('eq', 200);
            }
        });
    });

    describe('Course deletion', () => {
        beforeEach(() => {
            courseManagementRequest.createCourse(false, courseData.title, courseData.shortName).its('status').should('eq', 201);
        });

        it('Deletes an existing course', () => {
            navigationBar.openCourseManagement();
            courseManagement.openCourse(courseData.shortName);
            cy.get('#delete-course').click();
            cy.get(modalDeleteButton).should('be.disabled');
            cy.get('#confirm-exercise-name').type(courseData.title);
            cy.get(modalDeleteButton).should('not.be.disabled').click();
            courseManagement.getCourseCard(courseData.shortName).should('not.exist');
        });
    });

    describe('Course icon deletion', () => {
        let course: Course;
        let courseId: number;

        it('Deletes an existing course icon', () => {
            cy.fixture('course/icon.png', 'base64')
                .then(Cypress.Blob.base64StringToBlob)
                .then((blob) => {
                    courseManagementRequest
                        .createCourse(false, courseData.title, courseData.shortName, day().subtract(2, 'hours'), day().add(2, 'hours'), 'icon.png', blob)
                        .then((response) => {
                            course = convertCourseAfterMultiPart(response);
                            courseId = course.id!;
                            cy.intercept(PUT, BASE_API + 'courses/' + courseId).as('updateCourseQuery');
                        });
                });
            navigationBar.openCourseManagement();
            courseManagement.openCourse(courseData.shortName);
            cy.get('#edit-course').click();
            cy.get('#delete-course-icon').click();
            cy.get('#delete-course-icon').should('not.exist');
            cy.get('.no-image').should('exist');
            cy.get('#save-entity').click();
            cy.wait('@updateCourseQuery').then(() => {
                cy.get('#edit-course').click();
                cy.get('#delete-course-icon').should('not.exist');
                cy.get('.no-image').should('exist');
            });
        });

        it('Deletes not existing course icon', () => {
            courseManagementRequest.createCourse(false, courseData.title, courseData.shortName, day().subtract(2, 'hours'), day().add(2, 'hours')).then((response) => {
                course = convertCourseAfterMultiPart(response);
                courseId = course.id!;
            });
            navigationBar.openCourseManagement();
            courseManagement.openCourse(courseData.shortName);
            cy.get('#edit-course').click();
            cy.get('#delete-course-icon').should('not.exist');
            cy.get('.no-image').should('exist');
        });

        afterEach(() => {
            if (courseId) {
                courseManagementRequest.deleteCourse(courseId).its('status').should('eq', 200);
            }
        });
    });
});

function convertBooleanToYesNo(boolean: boolean) {
    return boolean ? 'Yes' : 'No';
}

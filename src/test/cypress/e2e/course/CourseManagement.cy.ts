import { Interception } from 'cypress/types/net-stubbing';
import { convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { BASE_API, PUT } from '../../support/constants';
import { artemis } from '../../support/ArtemisTesting';
import { dayjsToString, generateUUID, trimDate } from '../../support/utils';
import { Course } from 'app/entities/course.model';
import day from 'dayjs/esm';

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// User management
const users = artemis.users;
const student = users.getStudentOne();
const admin = users.getAdmin();

// PageObjects
const courseManagementPage = artemis.pageobjects.course.management;
const navigationBar = artemis.pageobjects.navigationBar;
const courseCreationPage = artemis.pageobjects.course.creation;

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
            courseManagementRequests.createCourse(false, courseData.title, courseData.shortName).then((response) => {
                course = convertCourseAfterMultiPart(response);
            });
        });

        it('Adds a student manually to the course', () => {
            const username = student.username;
            navigationBar.openCourseManagement();
            courseManagementPage.openCourse(courseData.shortName);
            courseManagementPage.addStudentToCourse(student);
            cy.get('#registered-students').contains(username).should('be.visible');
            navigationBar.openCourseManagement();
            courseManagementPage.openCourse(courseData.shortName);
            courseManagementPage.getCourseStudentGroupName().contains(`artemis-${courseData.shortName}-students (1)`);
        });

        it('Removes a student manually from the course', () => {
            const username = student.username;
            courseManagementRequests.addStudentToCourse(course, student);
            navigationBar.openCourseManagement();
            courseManagementPage.openStudentOverviewOfCourse(course.id!);
            cy.get('#registered-students').contains(username).should('be.visible');
            cy.get('#registered-students button[jhideletebutton]').should('be.visible').click();
            cy.get('.modal #delete').click();
            cy.get('#registered-students').contains(username).should('not.exist');
            navigationBar.openCourseManagement();
            courseManagementPage.openCourse(courseData.shortName);
            courseManagementPage.getCourseStudentGroupName().contains(`artemis-${courseData.shortName}-students (0)`);
        });

        after(() => {
            if (course) {
                courseManagementRequests.deleteCourse(course.id!).its('status').should('eq', 200);
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
            courseManagementPage.openCourseCreation();
            courseCreationPage.setTitle(courseData.title);
            courseCreationPage.setShortName(courseData.shortName);
            courseCreationPage.setDescription(courseData.description);
            courseCreationPage.setTestCourse(courseData.testCourse);
            courseCreationPage.setStartDate(courseData.startDate);
            courseCreationPage.setEndDate(courseData.endDate);
            courseCreationPage.setSemester(courseData.semester);
            courseCreationPage.setCourseMaxPoints(courseData.maxPoints);
            courseCreationPage.setProgrammingLanguage(courseData.programmingLanguage);
            courseCreationPage.setEnableComplaints(courseData.enableComplaints);
            courseCreationPage.setMaxComplaints(courseData.maxComplaints);
            courseCreationPage.setMaxTeamComplaints(courseData.maxTeamComplaints);
            courseCreationPage.setMaxComplaintsTimeDays(courseData.maxComplaintTimeDays);
            courseCreationPage.setEnableMoreFeedback(courseData.enableMoreFeedback);
            courseCreationPage.setMaxRequestMoreFeedbackTimeDays(courseData.maxRequestMoreFeedbackTimeDays);
            courseCreationPage.setOnlineCourse(courseData.onlineCourse);
            courseCreationPage.setPresentationScoreEnabled(courseData.presentationScoreEnabled);
            courseCreationPage.setPresentationScore(courseData.presentationScore);
            courseCreationPage.setCustomizeGroupNames(courseData.customizeGroupNames);
            courseCreationPage.submit().then((request: Interception) => {
                const courseBody = request.response!.body;
                courseId = courseBody.id!;
                expect(courseBody.title).to.eq(courseData.title);
                expect(courseBody.shortName).to.eq(courseData.shortName);
                expect(courseBody.description).to.eq(courseData.description);
                expect(courseBody.testCourse).to.eq(courseData.testCourse);
                expect(trimDate(courseBody.startDate)).to.eq(trimDate(dayjsToString(courseData.startDate)));
                expect(trimDate(courseBody.endDate)).to.eq(trimDate(dayjsToString(courseData.endDate)));
                expect(courseBody.validStartAndEndDate).to.eq(true);
                expect(courseBody.semester).to.eq(courseData.semester);
                expect(courseBody.maxPoints).to.eq(courseData.maxPoints);
                expect(courseBody.defaultProgrammingLanguage).to.eq(courseData.programmingLanguage);
                expect(courseBody.complaintsEnabled).to.eq(courseData.enableComplaints);
                expect(courseBody.maxComplaints).to.eq(courseData.maxComplaints);
                expect(courseBody.maxTeamComplaints).to.eq(courseData.maxTeamComplaints);
                expect(courseBody.maxComplaintTimeDays).to.eq(courseData.maxComplaintTimeDays);
                expect(courseBody.requestMoreFeedbackEnabled).to.eq(courseData.enableMoreFeedback);
                expect(courseBody.onlineCourse).to.eq(courseData.onlineCourse);
                expect(courseBody.presentationScore).to.eq(courseData.presentationScore);
                expect(courseBody.studentGroupName).to.eq(`artemis-${courseData.shortName}-students`);
                expect(courseBody.editorGroupName).to.eq(`artemis-${courseData.shortName}-editors`);
                expect(courseBody.instructorGroupName).to.eq(`artemis-${courseData.shortName}-instructors`);
                expect(courseBody.teachingAssistantGroupName).to.eq(`artemis-${courseData.shortName}-tutors`);
            });
            courseManagementPage.getCourseHeaderTitle().contains(courseData.title).should('be.visible');
            courseManagementPage.getCourseHeaderDescription().contains(courseData.description);
            courseManagementPage.getCourseTitle().contains(courseData.title);
            courseManagementPage.getCourseShortName().contains(courseData.shortName);
            courseManagementPage.getCourseStudentGroupName().contains(`artemis-${courseData.shortName}-students (0)`);
            courseManagementPage.getCourseTutorGroupName().contains(`artemis-${courseData.shortName}-tutors (0)`);
            courseManagementPage.getCourseEditorGroupName().contains(`artemis-${courseData.shortName}-editors (0)`);
            courseManagementPage.getCourseInstructorGroupName().contains(`artemis-${courseData.shortName}-instructors (0)`);
            courseManagementPage.getCourseStartDate().contains(courseData.startDate.format(dateFormat));
            courseManagementPage.getCourseEndDate().contains(courseData.endDate.format(dateFormat));
            courseManagementPage.getCourseSemester().contains(courseData.semester);
            courseManagementPage.getCourseProgrammingLanguage().contains(courseData.programmingLanguage);
            courseManagementPage.getCourseTestCourse().contains(convertBooleanToYesNo(courseData.testCourse));
            courseManagementPage.getCourseOnlineCourse().contains(convertBooleanToYesNo(courseData.onlineCourse));
            courseManagementPage.getCoursePresentationScoreEnabled().contains(convertBooleanToYesNo(courseData.presentationScoreEnabled));
            courseManagementPage.getCoursePresentationScore().contains(courseData.presentationScore);
            courseManagementPage.getCourseMaxComplaints().contains(courseData.maxComplaints);
            courseManagementPage.getCourseMaxTeamComplaints().contains(courseData.maxTeamComplaints);
            courseManagementPage.getMaxComplaintTimeDays().contains(courseData.maxComplaintTimeDays);
            courseManagementPage.getMaxRequestMoreFeedbackTimeDays().contains(courseData.maxRequestMoreFeedbackTimeDays);
        });

        if (allowGroupCustomization) {
            it('Creates a new course with custom groups', () => {
                navigationBar.openCourseManagement();
                courseManagementPage.openCourseCreation();
                courseCreationPage.setTitle(courseData.title);
                courseCreationPage.setShortName(courseData.shortName);
                courseCreationPage.setTestCourse(courseData.testCourse);
                courseCreationPage.setCustomizeGroupNames(true);
                courseCreationPage.setStudentGroup(courseData.studentGroupName);
                courseCreationPage.setTutorGroup(courseData.tutorGroupName);
                courseCreationPage.setEditorGroup(courseData.editorGroupName);
                courseCreationPage.setInstructorGroup(courseData.instructorGroupName);
                courseCreationPage.submit().then((request: Interception) => {
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
                courseManagementPage.getCourseHeaderTitle().contains(courseData.title).should('be.visible');
                courseManagementPage.getCourseTitle().contains(courseData.title);
                courseManagementPage.getCourseShortName().contains(courseData.shortName);
                courseManagementPage.getCourseTestCourse().contains(convertBooleanToYesNo(courseData.testCourse));
                courseManagementPage.getCourseStudentGroupName().contains(courseData.studentGroupName);
                courseManagementPage.getCourseTutorGroupName().contains(courseData.tutorGroupName);
                courseManagementPage.getCourseEditorGroupName().contains(courseData.editorGroupName);
                courseManagementPage.getCourseInstructorGroupName().contains(courseData.instructorGroupName);
            });
        }

        after(() => {
            if (courseId) {
                courseManagementRequests.deleteCourse(courseId).its('status').should('eq', 200);
            }
            if (courseId2) {
                courseManagementRequests.deleteCourse(courseId2).its('status').should('eq', 200);
            }
        });
    });

    describe('Course edit', () => {
        let courseId: number;
        const uid = generateUUID();
        editedCourseData.title = 'Cypress course' + uid;
        editedCourseData.shortName = 'cypress' + uid;

        beforeEach(() => {
            courseManagementRequests.createCourse(false, courseData.title, courseData.shortName).its('status').should('eq', 201);
        });

        it('Edits a existing course', () => {
            navigationBar.openCourseManagement();
            courseManagementPage.openCourse(courseData.shortName);
            courseManagementPage.openCourseEdit();

            courseCreationPage.setTitle(editedCourseData.title);
            courseCreationPage.setTestCourse(editedCourseData.testCourse);

            courseCreationPage.update().then((request: Interception) => {
                const courseBody = request.response!.body;
                courseId = courseBody.id!;
                expect(courseBody.title).to.eq(editedCourseData.title);
                expect(courseBody.shortName).to.eq(courseData.shortName);
                expect(courseBody.testCourse).to.eq(editedCourseData.testCourse);
            });
            courseManagementPage.getCourseHeaderTitle().contains(editedCourseData.title).should('be.visible');
            courseManagementPage.getCourseTitle().contains(editedCourseData.title);
            courseManagementPage.getCourseShortName().contains(courseData.shortName);
            courseManagementPage.getCourseTestCourse().contains(convertBooleanToYesNo(editedCourseData.testCourse));
        });

        after(() => {
            if (courseId) {
                courseManagementRequests.deleteCourse(courseId).its('status').should('eq', 200);
            }
        });
    });

    describe('Course deletion', () => {
        beforeEach(() => {
            courseManagementRequests.createCourse(false, courseData.title, courseData.shortName).its('status').should('eq', 201);
        });

        it('Deletes an existing course', () => {
            navigationBar.openCourseManagement();
            courseManagementPage.openCourse(courseData.shortName);
            cy.get('#delete-course').click();
            cy.get(modalDeleteButton).should('be.disabled');
            cy.get('#confirm-exercise-name').type(courseData.title);
            cy.get(modalDeleteButton).should('not.be.disabled').click();
            courseManagementPage.getCourseCard(courseData.shortName).should('not.exist');
        });
    });

    describe('Course icon deletion', () => {
        let course: Course;
        let courseId: number;

        it('Deletes an existing course icon', () => {
            cy.fixture('course/icon.png', 'base64')
                .then(Cypress.Blob.base64StringToBlob)
                .then((blob) => {
                    courseManagementRequests
                        .createCourse(false, courseData.title, courseData.shortName, day().subtract(2, 'hours'), day().add(2, 'hours'), 'icon.png', blob)
                        .then((response) => {
                            course = convertCourseAfterMultiPart(response);
                            courseId = course.id!;
                            cy.intercept(PUT, BASE_API + 'courses/' + courseId).as('updateCourseQuery');
                        });
                });
            navigationBar.openCourseManagement();
            courseManagementPage.openCourse(courseData.shortName);
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
            courseManagementRequests.createCourse(false, courseData.title, courseData.shortName, day().subtract(2, 'hours'), day().add(2, 'hours')).then((response) => {
                course = convertCourseAfterMultiPart(response);
                courseId = course.id!;
            });
            navigationBar.openCourseManagement();
            courseManagementPage.openCourse(courseData.shortName);
            cy.get('#edit-course').click();
            cy.get('#delete-course-icon').should('not.exist');
            cy.get('.no-image').should('exist');
        });

        afterEach(() => {
            if (courseId) {
                courseManagementRequests.deleteCourse(courseId).its('status').should('eq', 200);
            }
        });
    });
});

function convertBooleanToYesNo(boolean: boolean) {
    return boolean ? 'Yes' : 'No';
}

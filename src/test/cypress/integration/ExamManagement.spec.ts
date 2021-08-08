import { CypressExamBuilder } from '../support/requests/CourseManagementRequests';
import dayjs from 'dayjs';
import { artemis } from '../support/ArtemisTesting';
import { generateUUID } from '../support/utils';

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// Page objects
const navigationBar = artemis.pageobjects.navigationBar;
const courseManagement = artemis.pageobjects.courseManagement;
const examManagement = artemis.pageobjects.examManagement;

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;

describe('Exam management', () => {
    let course: any;
    let examTitle: string;

    before(() => {
        cy.login(artemis.users.getAdmin());
        courseManagementRequests.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
            courseManagementRequests.addStudentToCourse(course.id, artemis.users.getStudentOne().username);
        });
    });

    beforeEach(() => {
        examTitle = 'exam' + generateUUID();
        cy.login(artemis.users.getAdmin(), '/');
    });

    it('Creates an exam', function () {
        const creationPage = artemis.pageobjects.examCreation;
        navigationBar.openCourseManagement();
        courseManagement.openExamsOfCourse(courseName, courseShortName);

        examManagement.createNewExam();
        creationPage.setTitle(examTitle);
        creationPage.setVisibleDate(dayjs());
        creationPage.setStartDate(dayjs().add(1, 'day'));
        creationPage.setEndDate(dayjs().add(2, 'day'));
        creationPage.setNumberOfExercises(4);
        creationPage.setMaxPoints(40);

        creationPage.setStartText('Cypress exam start text');
        creationPage.setEndText('Cypress exam end text');
        creationPage.setConfirmationStartText('Cypress exam confirmation start text');
        creationPage.setConfirmationEndText('Cypress exam confirmation end text');
        creationPage.submit().its('response.statusCode').should('eq', 201);
        examManagement.getExamRow(examTitle).should('be.visible');
    });

    describe('Exam deletion', () => {
        beforeEach(() => {
            const exam = new CypressExamBuilder(course).title(examTitle).build();
            courseManagementRequests.createExam(exam);
        });

        it('Deletes an existing exam', () => {
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(courseName, courseShortName);
            examManagement.deleteExam(examTitle);
            examManagement.getExamSelector(examTitle).should('not.exist');
        });
    });

    describe('Exam timing', () => {
        let exam: any;

        it('Does not show exam before visible date', () => {
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().add(1, 'day'))
                .startDate(dayjs().add(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .build();
            courseManagementRequests.createExam(examContent).then((response) => {
                exam = response.body;
            });
            cy.login(artemis.users.getStudentOne(), `/courses`);
            cy.contains(examTitle).should('not.exist');
            cy.visit(`/courses/${course.id}`);
            cy.url().should('contain', `${course.id}`);
            cy.contains(examTitle).should('not.exist');
        });

        it('Shows after visible date', () => {
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(5, 'days'))
                .startDate(dayjs().add(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .build();
            courseManagementRequests.createExam(examContent).then((response) => {
                exam = response.body;
                courseManagementRequests.registerStudentForExam(course, exam, artemis.users.getStudentOne());
                cy.login(artemis.users.getStudentOne(), `/courses/${course.id}`);
                cy.url().should('contain', `${course.id}`);
                cy.contains('Exams').click();
                cy.url().should('contain', '/exams');
                cy.contains(examTitle).should('exist').click();
                cy.url().should('contain', `/exams/${exam.id}`);
            });
        });

        it('Student can start after start Date', () => {
            let exerciseGroup: any;
            let textExercise: any;
            const student = artemis.users.getStudentOne();
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .build();
            courseManagementRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                courseManagementRequests.registerStudentForExam(course, exam, student);
                courseManagementRequests.addExerciseGroupForExam(course, exam, 'group 1', true).then((groupResponse) => {
                    exerciseGroup = groupResponse.body;
                    courseManagementRequests.addTextExerciseToExam(exerciseGroup, 'Text exercise 1').then((exerciseResponse) => {
                        textExercise = exerciseResponse.body;
                        courseManagementRequests.generateMissingIndividualExams(course, exam);
                        courseManagementRequests.prepareExerciseStartForExam(course, exam);
                        cy.login(student, `/courses/${course.id}/exams`);
                        cy.contains(exam.title).click();
                        cy.url().should('contain', `/exams/${exam.id}`);
                        cy.contains('Welcome to ' + exam.title).should('exist');
                        cy.get('#confirmBox').click();
                        artemis.users.getAccountInfo((account: any) => cy.get('#fullname').type(account.firstName + ' ' + account.lastName));
                        cy.contains('Start').click();
                        cy.contains('Exam Overview').should('exist');
                        cy.intercept('PUT', `/api/exercises/${textExercise.id}/text-submissions`).as('savedSubmission');
                        cy.contains('Text exercise 1').should('exist').click();
                        cy.get('#text-editor-tab').type(
                            'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.',
                        );
                        cy.contains('Save').click();
                        cy.wait('@savedSubmission');
                    });
                });
            });
        });

        it('Exam ends after end time', () => {
            let exerciseGroup: any;
            const student = artemis.users.getStudentOne();
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(dayjs().subtract(119, 'minutes').subtract(45, 'seconds'))
                .build();
            courseManagementRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                courseManagementRequests.registerStudentForExam(course, exam, student);
                courseManagementRequests.addExerciseGroupForExam(course, exam, 'group 1', true).then((groupResponse) => {
                    exerciseGroup = groupResponse.body;
                    courseManagementRequests.addTextExerciseToExam(exerciseGroup, 'Text exercise 1').then(() => {
                        courseManagementRequests.generateMissingIndividualExams(course, exam);
                        courseManagementRequests.prepareExerciseStartForExam(course, exam);
                        cy.login(student, `/courses/${course.id}/exams/${exam.id}`);
                        cy.get('#confirmBox').click();
                        artemis.users.getAccountInfo((account: any) => cy.get('#fullname').type(account.firstName + ' ' + account.lastName));
                        cy.contains('Start').click();
                        cy.contains('Text exercise 1').should('exist').click();
                        cy.get('#text-editor-tab').type(
                            'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.',
                        );
                        cy.intercept('GET', '/api/notifications?page=*').as('examEnd');
                        cy.contains('Save').click();
                        cy.wait('@examEnd', { timeout: 20000 });
                        cy.contains('This is the end of ' + exam.title);
                        cy.get('#confirmBox').click();
                        artemis.users.getAccountInfo((account: any) => cy.get('#fullname').type(account.firstName + ' ' + account.lastName));
                        cy.get('.btn').click();
                        cy.get('.alert').contains('Your exam was submitted successfully.');
                    });
                });
            });
        });

        afterEach(() => {
            cy.login(artemis.users.getAdmin());
            courseManagementRequests.deleteExam(course, exam);
        });
    });

    after(() => {
        if (!!course) {
            cy.login(artemis.users.getAdmin());
            courseManagementRequests.deleteCourse(course.id);
        }
    });
});

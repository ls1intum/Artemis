import { Interception } from 'cypress/types/net-stubbing';
import { Course } from 'app/entities/course.model';
import { CypressExamBuilder, convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import dayjs from 'dayjs/esm';
import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// Pageobjects
const navigationBar = artemis.pageobjects.navigationBar;
const courseManagement = artemis.pageobjects.course.management;
const examManagement = artemis.pageobjects.exam.management;
const creationPage = artemis.pageobjects.exam.creation;
const examDetailsPage = artemis.pageobjects.exam.details;

const examData = {
    title: 'exam' + generateUUID(),
    visibleDate: dayjs(),
    startDate: dayjs().add(1, 'day'),
    endDate: dayjs().add(2, 'day'),
    numberOfExercises: 4,
    maxPoints: 40,
    startText: 'Cypress exam start text',
    endText: 'Cypress exam end text',
    confirmationStartText: 'Cypress exam confirmation start text',
    confirmationEndText: 'Cypress exam confirmation end text',
};

const editedExamData = {
    title: 'exam' + generateUUID(),
    visibleDate: dayjs(),
    startDate: dayjs().add(2, 'day'),
    endDate: dayjs().add(4, 'day'),
    numberOfExercises: 3,
    maxPoints: 30,
    startText: 'Edited cypress exam start text',
    endText: 'Edited cypress exam end text',
    confirmationStartText: 'Edited cypress exam confirmation start text',
    confirmationEndText: 'Edited cypress exam confirmation end text',
};

const dateFormat = 'MMM D, YYYY HH:mm';

describe('Exam creation/deletion', () => {
    let course: Course;
    let examId: number;

    before(() => {
        cy.login(artemis.users.getAdmin());
        courseManagementRequests.createCourse().then((response) => {
            course = convertCourseAfterMultiPart(response);
        });
    });

    beforeEach(() => {
        cy.login(artemis.users.getAdmin(), '/');
    });

    it('Creates an exam', () => {
        navigationBar.openCourseManagement();
        courseManagement.openExamsOfCourse(course.shortName!);

        examManagement.createNewExam();
        creationPage.setTitle(examData.title);
        creationPage.setVisibleDate(examData.visibleDate);
        creationPage.setStartDate(examData.startDate);
        creationPage.setEndDate(examData.endDate);
        creationPage.setNumberOfExercises(examData.numberOfExercises);
        creationPage.setExamMaxPoints(examData.maxPoints);

        creationPage.setStartText(examData.startText);
        creationPage.setEndText(examData.endText);
        creationPage.setConfirmationStartText(examData.confirmationStartText);
        creationPage.setConfirmationEndText(examData.confirmationEndText);
        creationPage.submit().then((examResponse: Interception) => {
            examId = examResponse.response!.body.id;
            expect(examResponse.response!.statusCode).to.eq(201);
            cy.url().should('contain', `/exams/${examId}`);
        });
        cy.get('#exam-detail-title').should('contain.text', examData.title);
        cy.get('#exam-visible-date').should('contain.text', examData.visibleDate.format(dateFormat));
        cy.get('#exam-start-date').should('contain.text', examData.startDate.format(dateFormat));
        cy.get('#exam-end-date').should('contain.text', examData.endDate.format(dateFormat));
        cy.get('#exam-number-of-exercises').should('contain.text', examData.numberOfExercises);
        cy.get('#exam-max-points').should('contain.text', examData.maxPoints);
        cy.get('#exam-start-text').should('contain.text', examData.startText);
        cy.get('#exam-end-text').should('contain.text', examData.endText);
        cy.get('#exam-confirmation-start-text').should('contain.text', examData.confirmationStartText);
        cy.get('#exam-confirmation-end-text').should('contain.text', examData.confirmationEndText);
        cy.get('#exam-working-time').should('contain.text', '1d 0h');
    });

    describe('Exam deletion', () => {
        beforeEach(() => {
            examData.title = 'exam' + generateUUID();
            const exam = new CypressExamBuilder(course).title(examData.title).build();
            courseManagementRequests.createExam(exam).then((examResponse) => {
                examId = examResponse.body.id;
            });
        });

        it('Deletes an existing exam', () => {
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(course.shortName!);
            examManagement.openExam(examId);
            examDetailsPage.deleteExam(examData.title);
            examManagement.getExamSelector(examData.title).should('not.exist');
        });
    });

    describe('Edits an exam', () => {
        beforeEach(() => {
            examData.title = 'exam' + generateUUID();
            const exam = new CypressExamBuilder(course).title(examData.title).build();
            courseManagementRequests.createExam(exam).then((examResponse) => {
                examId = examResponse.body.id;
            });
        });

        it('Edits an existing exam', () => {
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(course.shortName!);
            examManagement.openExam(examId);
            cy.get('#exam-detail-title').should('contain.text', examData.title);
            cy.get('#editButton').click();

            creationPage.setTitle(editedExamData.title);
            creationPage.setVisibleDate(editedExamData.visibleDate);
            creationPage.setStartDate(editedExamData.startDate);
            creationPage.setEndDate(editedExamData.endDate);
            creationPage.setNumberOfExercises(editedExamData.numberOfExercises);
            creationPage.setExamMaxPoints(editedExamData.maxPoints);

            creationPage.setStartText(editedExamData.startText);
            creationPage.setEndText(editedExamData.endText);
            creationPage.setConfirmationStartText(editedExamData.confirmationStartText);
            creationPage.setConfirmationEndText(editedExamData.confirmationEndText);
            creationPage.update().then((examResponse: Interception) => {
                examId = examResponse.response!.body.id;
                expect(examResponse.response!.statusCode).to.eq(200);
                cy.url().should('contain', `/exams/${examId}`);
            });
            cy.get('#exam-detail-title').should('contain.text', editedExamData.title);
            cy.get('#exam-visible-date').should('contain.text', editedExamData.visibleDate.format(dateFormat));
            cy.get('#exam-start-date').should('contain.text', editedExamData.startDate.format(dateFormat));
            cy.get('#exam-end-date').should('contain.text', editedExamData.endDate.format(dateFormat));
            cy.get('#exam-number-of-exercises').should('contain.text', editedExamData.numberOfExercises);
            cy.get('#exam-max-points').should('contain.text', editedExamData.maxPoints);
            cy.get('#exam-start-text').should('contain.text', editedExamData.startText);
            cy.get('#exam-end-text').should('contain.text', editedExamData.endText);
            cy.get('#exam-confirmation-start-text').should('contain.text', editedExamData.confirmationStartText);
            cy.get('#exam-confirmation-end-text').should('contain.text', editedExamData.confirmationEndText);
            cy.get('#exam-working-time').should('contain.text', '2d 0h');
        });
    });

    after(() => {
        if (course) {
            courseManagementRequests.deleteCourse(course.id!);
        }
    });
});

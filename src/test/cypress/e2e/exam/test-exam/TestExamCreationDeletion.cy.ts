import { Interception } from 'cypress/types/net-stubbing';
import { Course } from 'app/entities/course.model';
import { CypressExamBuilder, convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import dayjs from 'dayjs/esm';
import { artemis } from '../../../support/ArtemisTesting';
import { dayjsToString, generateUUID, trimDate } from '../../../support/utils';

// Users
const users = artemis.users;
const admin = users.getAdmin();

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// PageObjects
const navigationBar = artemis.pageobjects.navigationBar;
const courseManagement = artemis.pageobjects.course.management;
const examManagement = artemis.pageobjects.exam.management;
const creationPage = artemis.pageobjects.exam.creation;
const examDetailsPage = artemis.pageobjects.exam.details;

// Common primitives
const examData = {
    title: 'exam' + generateUUID(),
    visibleDate: dayjs(),
    startDate: dayjs().add(1, 'day'),
    endDate: dayjs().add(2, 'day'),
    workingTime: 5,
    numberOfExercises: 4,
    maxPoints: 40,
    startText: 'Cypress exam start text',
    endText: 'Cypress exam end text',
    confirmationStartText: 'Cypress exam confirmation start text',
    confirmationEndText: 'Cypress exam confirmation end text',
};

describe('Test Exam creation/deletion', () => {
    let course: Course;
    let examId: number;

    before(() => {
        cy.login(admin);
        courseManagementRequests.createCourse().then((response) => {
            course = convertCourseAfterMultiPart(response);
        });
    });

    beforeEach(() => {
        cy.login(admin, '/');
    });

    it('Creates a test exam', function () {
        navigationBar.openCourseManagement();
        courseManagement.openExamsOfCourse(course.shortName!);

        examManagement.createNewExam();
        creationPage.setTitle(examData.title);
        creationPage.setTestMode();
        creationPage.setVisibleDate(examData.visibleDate);
        creationPage.setStartDate(examData.startDate);
        creationPage.setEndDate(examData.endDate);
        creationPage.setWorkingTime(examData.workingTime);
        creationPage.setNumberOfExercises(examData.numberOfExercises);
        creationPage.setExamMaxPoints(examData.maxPoints);

        creationPage.setStartText(examData.startText);
        creationPage.setEndText(examData.endText);
        creationPage.setConfirmationStartText(examData.confirmationStartText);
        creationPage.setConfirmationEndText(examData.confirmationEndText);

        creationPage.submit().then((examResponse: Interception) => {
            const examBody = examResponse.response!.body;
            examId = examResponse.response!.body.id;
            expect(examResponse.response!.statusCode).to.eq(201);
            expect(examBody.title).to.eq(examData.title);
            expect(examBody.testExam).to.be.true;
            expect(trimDate(examBody.visibleDate)).to.eq(trimDate(dayjsToString(examData.visibleDate)));
            expect(trimDate(examBody.startDate)).to.eq(trimDate(dayjsToString(examData.startDate)));
            expect(trimDate(examBody.endDate)).to.eq(trimDate(dayjsToString(examData.endDate)));
            expect(examBody.workingTime).to.eq(examData.workingTime * 60);
            expect(examBody.numberOfExercisesInExam).to.eq(examData.numberOfExercises);
            expect(examBody.examMaxPoints).to.eq(examData.maxPoints);
            expect(examBody.startText).to.eq(examData.startText);
            expect(examBody.endText).to.eq(examData.endText);
            expect(examBody.confirmationStartText).to.eq(examData.confirmationStartText);
            expect(examBody.confirmationEndText).to.eq(examData.confirmationEndText);
            cy.url().should('contain', `/exams/${examId}`);
        });
        cy.get('#exam-detail-title').should('contain.text', examData.title);
    });

    describe('Test exam deletion', () => {
        beforeEach(() => {
            examData.title = 'exam' + generateUUID();
            const exam = new CypressExamBuilder(course).title(examData.title).testExam().build();
            courseManagementRequests.createExam(exam).then((examResponse) => {
                examId = examResponse.body.id;
            });
        });

        it('Deletes an existing test exam', () => {
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(course.shortName!);
            examManagement.getExamSelector(examData.title).should('exist');
            examManagement.openExam(examId);
            examDetailsPage.deleteExam(examData.title);
            examManagement.getExamSelector(examData.title).should('not.exist');
        });
    });

    after(() => {
        if (course) {
            courseManagementRequests.deleteCourse(course.id!);
        }
    });
});

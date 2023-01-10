import { Interception } from 'cypress/types/net-stubbing';
import { Course } from 'app/entities/course.model';
import { CypressExamBuilder, convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import dayjs from 'dayjs/esm';
import { artemis } from '../../../support/ArtemisTesting';
import { generateUUID } from '../../../support/utils';

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
    workingTime: 5,
    numberOfExercises: 4,
    maxPoints: 40,
    startText: 'Cypress exam start text',
    endText: 'Cypress exam end text',
    confirmationStartText: 'Cypress exam confirmation start text',
    confirmationEndText: 'Cypress exam confirmation end text',
};

const dateFormat = 'YYYY-MM-DDTHH:mm:ss';

describe('Test Exam creation/deletion', () => {
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
            examId = examResponse.response!.body.id;
            expect(examResponse.response!.statusCode).to.eq(201);
            expect(examResponse.response!.body.title).to.eq(examData.title);
            expect(examResponse.response!.body.testExam).to.be.true;
            expect(trimDate(examResponse.response!.body.visibleDate)).to.eq(examData.visibleDate.utcOffset(0).format(dateFormat));
            expect(trimDate(examResponse.response!.body.startDate)).to.eq(examData.startDate.utcOffset(0).format(dateFormat));
            expect(trimDate(examResponse.response!.body.endDate)).to.eq(examData.endDate.utcOffset(0).format(dateFormat));
            expect(examResponse.response!.body.workingTime).to.eq(examData.workingTime * 60);
            expect(examResponse.response!.body.numberOfExercisesInExam).to.eq(examData.numberOfExercises);
            expect(examResponse.response!.body.examMaxPoints).to.eq(examData.maxPoints);
            expect(examResponse.response!.body.startText).to.eq(examData.startText);
            expect(examResponse.response!.body.endText).to.eq(examData.endText);
            expect(examResponse.response!.body.confirmationStartText).to.eq(examData.confirmationStartText);
            expect(examResponse.response!.body.confirmationEndText).to.eq(examData.confirmationEndText);
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

// This is necessary, to align the javascript time format with the java time format
function trimDate(date: string) {
    return date.slice(0, 19);
}

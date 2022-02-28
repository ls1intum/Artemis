import { Interception } from 'cypress/types/net-stubbing';
import { Course } from 'app/entities/course.model';
import { CypressExamBuilder } from '../../support/requests/CourseManagementRequests';
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

describe('Exam creation/deletion', () => {
    let course: Course;
    let examTitle: string;
    let examId: number;

    before(() => {
        cy.login(artemis.users.getAdmin());
        courseManagementRequests.createCourse().then((response) => {
            course = response.body;
        });
    });

    beforeEach(() => {
        examTitle = 'exam' + generateUUID();
        cy.login(artemis.users.getAdmin(), '/');
    });

    it('Creates an exam', function () {
        navigationBar.openCourseManagement();
        courseManagement.openExamsOfCourse(course.shortName!);

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
        creationPage.submit().then((examResponse: Interception) => {
            examId = examResponse.response!.body.id;
            expect(examResponse.response!.statusCode).to.eq(201);
        });
        examManagement.getExamRowRoot(examTitle).should('be.visible');
    });

    describe('Exam deletion', () => {
        beforeEach(() => {
            const exam = new CypressExamBuilder(course).title(examTitle).build();
            courseManagementRequests.createExam(exam).then((examResponse) => {
                examId = examResponse.body.id;
            });
        });

        it('Deletes an existing exam', () => {
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(course.shortName!);
            examManagement.openExam(examId);
            examDetailsPage.deleteExam(examTitle);
            examManagement.getExamSelector(examTitle).should('not.exist');
        });
    });

    after(() => {
        if (!!course) {
            courseManagementRequests.deleteCourse(course.id!);
        }
    });
});

import { Interception } from 'cypress/types/net-stubbing';
import { Course } from 'app/entities/course.model';
import { CypressExamBuilder, convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import dayjs from 'dayjs/esm';
import { artemis } from '../../support/ArtemisTesting';
import { dayjsToString, generateUUID, trimDate } from '../../support/utils';

// Users
const users = artemis.users;
const admin = users.getAdmin();

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// PageObjects
const navigationBar = artemis.pageobjects.navigationBar;
const courseManagementPage = artemis.pageobjects.course.management;
const examManagementPage = artemis.pageobjects.exam.management;
const examCreationPage = artemis.pageobjects.exam.creation;
const examDetailsPage = artemis.pageobjects.exam.details;

// Common primitives
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
        cy.login(admin);
        courseManagementRequests.createCourse().then((response) => {
            course = convertCourseAfterMultiPart(response);
        });
    });

    beforeEach(() => {
        cy.login(admin, '/');
    });

    it('Creates an exam', () => {
        navigationBar.openCourseManagement();
        courseManagementPage.openExamsOfCourse(course.shortName!);

        examManagementPage.createNewExam();
        examCreationPage.setTitle(examData.title);
        examCreationPage.setVisibleDate(examData.visibleDate);
        examCreationPage.setStartDate(examData.startDate);
        examCreationPage.setEndDate(examData.endDate);
        examCreationPage.setNumberOfExercises(examData.numberOfExercises);
        examCreationPage.setExamMaxPoints(examData.maxPoints);

        examCreationPage.setStartText(examData.startText);
        examCreationPage.setEndText(examData.endText);
        examCreationPage.setConfirmationStartText(examData.confirmationStartText);
        examCreationPage.setConfirmationEndText(examData.confirmationEndText);
        examCreationPage.submit().then((examResponse: Interception) => {
            const examBody = examResponse.response!.body;
            examId = examBody.id;
            expect(examResponse.response!.statusCode).to.eq(201);
            expect(examBody.testExam).to.be.false;
            expect(trimDate(examBody.visibleDate)).to.eq(trimDate(dayjsToString(examData.visibleDate)));
            expect(trimDate(examBody.startDate)).to.eq(trimDate(dayjsToString(examData.startDate)));
            expect(trimDate(examBody.endDate)).to.eq(trimDate(dayjsToString(examData.endDate)));
            expect(examBody.numberOfExercisesInExam).to.eq(examData.numberOfExercises);
            expect(examBody.examMaxPoints).to.eq(examData.maxPoints);
            expect(examBody.startText).to.eq(examData.startText);
            expect(examBody.endText).to.eq(examData.endText);
            expect(examBody.confirmationStartText).to.eq(examData.confirmationStartText);
            expect(examBody.confirmationEndText).to.eq(examData.confirmationEndText);
            cy.url().should('contain', `/exams/${examId}`);
        });
        examManagementPage.getExamTitle().contains(examData.title);
        examManagementPage.getExamVisibleDate().contains(examData.visibleDate.format(dateFormat));
        examManagementPage.getExamStartDate().contains(examData.startDate.format(dateFormat));
        examManagementPage.getExamEndDate().contains(examData.endDate.format(dateFormat));
        examManagementPage.getExamNumberOfExercises().contains(examData.numberOfExercises);
        examManagementPage.getExamMaxPoints().contains(examData.maxPoints);
        examManagementPage.getExamStartText().contains(examData.startText);
        examManagementPage.getExamEndText().contains(examData.endText);
        examManagementPage.getExamConfirmationStartText().contains(examData.confirmationStartText);
        examManagementPage.getExamConfirmationEndText().contains(examData.confirmationEndText);
        examManagementPage.getExamWorkingTime().contains('1d 0h');
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
            courseManagementPage.openExamsOfCourse(course.shortName!);
            examManagementPage.openExam(examId);
            examDetailsPage.deleteExam(examData.title);
            examManagementPage.getExamSelector(examData.title).should('not.exist');
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
            courseManagementPage.openExamsOfCourse(course.shortName!);
            examManagementPage.openExam(examId);
            cy.get('#exam-detail-title').contains(examData.title);
            cy.get('#editButton').click();

            examCreationPage.setTitle(editedExamData.title);
            examCreationPage.setVisibleDate(editedExamData.visibleDate);
            examCreationPage.setStartDate(editedExamData.startDate);
            examCreationPage.setEndDate(editedExamData.endDate);
            examCreationPage.setNumberOfExercises(editedExamData.numberOfExercises);
            examCreationPage.setExamMaxPoints(editedExamData.maxPoints);

            examCreationPage.setStartText(editedExamData.startText);
            examCreationPage.setEndText(editedExamData.endText);
            examCreationPage.setConfirmationStartText(editedExamData.confirmationStartText);
            examCreationPage.setConfirmationEndText(editedExamData.confirmationEndText);
            examCreationPage.update().then((examResponse: Interception) => {
                const examBody = examResponse.response!.body;
                examId = examBody.id;
                expect(examResponse.response!.statusCode).to.eq(200);
                expect(examBody.testExam).to.be.false;
                expect(trimDate(examBody.visibleDate)).to.eq(trimDate(dayjsToString(editedExamData.visibleDate)));
                expect(trimDate(examBody.startDate)).to.eq(trimDate(dayjsToString(editedExamData.startDate)));
                expect(trimDate(examBody.endDate)).to.eq(trimDate(dayjsToString(editedExamData.endDate)));
                expect(examBody.numberOfExercisesInExam).to.eq(editedExamData.numberOfExercises);
                expect(examBody.examMaxPoints).to.eq(editedExamData.maxPoints);
                expect(examBody.startText).to.eq(editedExamData.startText);
                expect(examBody.endText).to.eq(editedExamData.endText);
                expect(examBody.confirmationStartText).to.eq(editedExamData.confirmationStartText);
                expect(examBody.confirmationEndText).to.eq(editedExamData.confirmationEndText);
                cy.url().should('contain', `/exams/${examId}`);
            });
            examManagementPage.getExamTitle().contains(editedExamData.title);
            examManagementPage.getExamVisibleDate().contains(editedExamData.visibleDate.format(dateFormat));
            examManagementPage.getExamStartDate().contains(editedExamData.startDate.format(dateFormat));
            examManagementPage.getExamEndDate().contains(editedExamData.endDate.format(dateFormat));
            examManagementPage.getExamNumberOfExercises().contains(editedExamData.numberOfExercises);
            examManagementPage.getExamMaxPoints().contains(editedExamData.maxPoints);
            examManagementPage.getExamStartText().contains(editedExamData.startText);
            examManagementPage.getExamEndText().contains(editedExamData.endText);
            examManagementPage.getExamConfirmationStartText().contains(editedExamData.confirmationStartText);
            examManagementPage.getExamConfirmationEndText().contains(editedExamData.confirmationEndText);
            examManagementPage.getExamWorkingTime().contains('2d 0h');
        });
    });

    after(() => {
        if (course) {
            courseManagementRequests.deleteCourse(course.id!);
        }
    });
});

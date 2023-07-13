import { Interception } from 'cypress/types/net-stubbing';
import { Course } from 'app/entities/course.model';
import { ExamBuilder, convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import dayjs from 'dayjs/esm';
import { dayjsToString, generateUUID, trimDate } from '../../../support/utils';
import { courseManagement, courseManagementRequest, examCreation, examDetails, examManagement, navigationBar } from '../../../support/artemis';
import { admin } from '../../../support/users';

// Common primitives
const examData = {
    title: 'exam' + generateUUID(),
    visibleDate: dayjs(),
    startDate: dayjs().add(1, 'day'),
    endDate: dayjs().add(2, 'day'),
    workingTime: 5,
    numberOfExercises: 4,
    maxPoints: 40,
    startText: 'Exam start text',
    endText: 'Exam end text',
    confirmationStartText: 'Exam confirmation start text',
    confirmationEndText: 'Exam confirmation end text',
};

describe('Test Exam creation/deletion', () => {
    let course: Course;
    let examId: number;

    before(() => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
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
        examCreation.setTitle(examData.title);
        examCreation.setTestMode();
        examCreation.setVisibleDate(examData.visibleDate);
        examCreation.setStartDate(examData.startDate);
        examCreation.setEndDate(examData.endDate);
        examCreation.setWorkingTime(examData.workingTime);
        examCreation.setNumberOfExercises(examData.numberOfExercises);
        examCreation.setExamMaxPoints(examData.maxPoints);

        examCreation.setStartText(examData.startText);
        examCreation.setEndText(examData.endText);
        examCreation.setConfirmationStartText(examData.confirmationStartText);
        examCreation.setConfirmationEndText(examData.confirmationEndText);

        examCreation.submit().then((examResponse: Interception) => {
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
            const exam = new ExamBuilder(course).title(examData.title).testExam().build();
            courseManagementRequest.createExam(exam).then((examResponse) => {
                examId = examResponse.body.id;
            });
        });

        it('Deletes an existing test exam', () => {
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(course.shortName!);
            examManagement.getExamSelector(examData.title).should('exist');
            examManagement.openExam(examId);
            examDetails.deleteExam(examData.title);
            examManagement.getExamSelector(examData.title).should('not.exist');
        });
    });

    after(() => {
        if (course) {
            courseManagementRequest.deleteCourse(course.id!);
        }
    });
});

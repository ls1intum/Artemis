import { Interception } from 'cypress/types/net-stubbing';
import dayjs from 'dayjs/esm';

import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

import { courseManagement, courseManagementAPIRequest, examAPIRequests, examCreation, examDetails, examManagement, navigationBar } from '../../../support/artemis';
import { admin } from '../../../support/users';
import { convertModelAfterMultiPart, dayjsToString, generateUUID, trimDate } from '../../../support/utils';

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
    let exam: Exam;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
        });
    });

    beforeEach(() => {
        cy.login(admin, '/');
    });

    it('Creates a test exam', function () {
        navigationBar.openCourseManagement();
        courseManagement.openExamsOfCourse(course.id!);

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
            exam = examResponse.response!.body;
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
            cy.url().should('contain', `/exams/${exam.id}`);
        });
        examManagement.getExamTitle().contains(examData.title);
    });

    describe('Test exam deletion', () => {
        beforeEach(() => {
            examData.title = 'exam' + generateUUID();
            const examConfig: Exam = {
                course,
                title: examData.title,
                testExam: true,
            };
            examAPIRequests.createExam(examConfig).then((examResponse) => {
                exam = examResponse.body;
            });
        });

        it('Deletes an existing test exam', () => {
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(course.id!);
            examManagement.getExamSelector(examData.title).should('exist');
            examManagement.openExam(exam.id!);
            examDetails.deleteExam(examData.title);
            examManagement.getExamSelector(examData.title).should('not.exist');
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});

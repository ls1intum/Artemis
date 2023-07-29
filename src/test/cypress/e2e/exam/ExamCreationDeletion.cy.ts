import { Interception } from 'cypress/types/net-stubbing';
import { Course } from 'app/entities/course.model';
import { ExamBuilder, convertModelAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import dayjs from 'dayjs/esm';
import { dayjsToString, generateUUID, trimDate } from '../../support/utils';
import { courseManagement, courseManagementRequest, examCreation, examDetails, examManagement, navigationBar } from '../../support/artemis';
import { admin } from '../../support/users';

// Common primitives
const examData = {
    title: 'exam' + generateUUID(),
    visibleDate: dayjs(),
    startDate: dayjs().add(1, 'day'),
    endDate: dayjs().add(2, 'day'),
    numberOfExercises: 4,
    maxPoints: 40,
    startText: 'Exam start text',
    endText: 'Exam end text',
    confirmationStartText: 'Exam confirmation start text',
    confirmationEndText: 'Exam confirmation end text',
};

const editedExamData = {
    title: 'exam' + generateUUID(),
    visibleDate: dayjs(),
    startDate: dayjs().add(2, 'day'),
    endDate: dayjs().add(4, 'day'),
    numberOfExercises: 3,
    maxPoints: 30,
    startText: 'Edited exam start text',
    endText: 'Edited exam end text',
    confirmationStartText: 'Edited exam confirmation start text',
    confirmationEndText: 'Edited exam confirmation end text',
};

const dateFormat = 'MMM D, YYYY HH:mm';

describe('Exam creation/deletion', () => {
    let course: Course;
    let examId: number;

    before('Create course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
        });
    });

    beforeEach(() => {
        cy.login(admin, '/');
    });

    it('Creates an exam', () => {
        navigationBar.openCourseManagement();
        courseManagement.openExamsOfCourse(course.id!);

        examManagement.createNewExam();
        examCreation.setTitle(examData.title);
        examCreation.setVisibleDate(examData.visibleDate);
        examCreation.setStartDate(examData.startDate);
        examCreation.setEndDate(examData.endDate);
        examCreation.setNumberOfExercises(examData.numberOfExercises);
        examCreation.setExamMaxPoints(examData.maxPoints);

        examCreation.setStartText(examData.startText);
        examCreation.setEndText(examData.endText);
        examCreation.setConfirmationStartText(examData.confirmationStartText);
        examCreation.setConfirmationEndText(examData.confirmationEndText);
        examCreation.submit().then((examResponse: Interception) => {
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
        examManagement.getExamTitle().contains(examData.title);
        examManagement.getExamVisibleDate().contains(examData.visibleDate.format(dateFormat));
        examManagement.getExamStartDate().contains(examData.startDate.format(dateFormat));
        examManagement.getExamEndDate().contains(examData.endDate.format(dateFormat));
        examManagement.getExamNumberOfExercises().contains(examData.numberOfExercises);
        examManagement.getExamMaxPoints().contains(examData.maxPoints);
        examManagement.getExamStartText().contains(examData.startText);
        examManagement.getExamEndText().contains(examData.endText);
        examManagement.getExamConfirmationStartText().contains(examData.confirmationStartText);
        examManagement.getExamConfirmationEndText().contains(examData.confirmationEndText);
        examManagement.getExamWorkingTime().contains('1d 0h');
    });

    describe('Exam deletion', () => {
        beforeEach(() => {
            examData.title = 'exam' + generateUUID();
            const exam = new ExamBuilder(course).title(examData.title).build();
            courseManagementRequest.createExam(exam).then((examResponse) => {
                examId = examResponse.body.id;
            });
        });

        it('Deletes an existing exam', () => {
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(course.id!);
            examManagement.openExam(examId);
            examDetails.deleteExam(examData.title);
            examManagement.getExamSelector(examData.title).should('not.exist');
        });
    });

    describe('Edits an exam', () => {
        beforeEach(() => {
            examData.title = 'exam' + generateUUID();
            const exam = new ExamBuilder(course).title(examData.title).build();
            courseManagementRequest.createExam(exam).then((examResponse) => {
                examId = examResponse.body.id;
            });
        });

        it('Edits an existing exam', () => {
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(course.id!);
            examManagement.openExam(examId);
            examManagement.getExamTitle().contains(examData.title);
            examManagement.clickEdit();

            examCreation.setTitle(editedExamData.title);
            examCreation.setVisibleDate(editedExamData.visibleDate);
            examCreation.setStartDate(editedExamData.startDate);
            examCreation.setEndDate(editedExamData.endDate);
            examCreation.setNumberOfExercises(editedExamData.numberOfExercises);
            examCreation.setExamMaxPoints(editedExamData.maxPoints);

            examCreation.setStartText(editedExamData.startText);
            examCreation.setEndText(editedExamData.endText);
            examCreation.setConfirmationStartText(editedExamData.confirmationStartText);
            examCreation.setConfirmationEndText(editedExamData.confirmationEndText);
            examCreation.update().then((examResponse: Interception) => {
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
            examManagement.getExamTitle().contains(editedExamData.title);
            examManagement.getExamVisibleDate().contains(editedExamData.visibleDate.format(dateFormat));
            examManagement.getExamStartDate().contains(editedExamData.startDate.format(dateFormat));
            examManagement.getExamEndDate().contains(editedExamData.endDate.format(dateFormat));
            examManagement.getExamNumberOfExercises().contains(editedExamData.numberOfExercises);
            examManagement.getExamMaxPoints().contains(editedExamData.maxPoints);
            examManagement.getExamStartText().contains(editedExamData.startText);
            examManagement.getExamEndText().contains(editedExamData.endText);
            examManagement.getExamConfirmationStartText().contains(editedExamData.confirmationStartText);
            examManagement.getExamConfirmationEndText().contains(editedExamData.confirmationEndText);
            examManagement.getExamWorkingTime().contains('2d 0h');
        });
    });

    after('Delete course', () => {
        courseManagementRequest.deleteCourse(course, admin);
    });
});

import { Interception } from 'cypress/types/net-stubbing';
import dayjs, { Dayjs } from 'dayjs/esm';

import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

import javaPartiallySuccessful from '../../fixtures/exercise/programming/java/partially_successful/submission.json';
import {
    courseAssessment,
    courseManagementAPIRequest,
    examAPIRequests,
    examAssessment,
    examExerciseGroupCreation,
    examManagement,
    examNavigation,
    examParticipation,
    examStartEnd,
    exerciseAssessment,
    modelingExerciseAssessment,
    studentAssessment,
} from '../../support/artemis';
import { Exercise, ExerciseType, ProgrammingExerciseAssessmentType } from '../../support/constants';
import { admin, instructor, studentOne, tutor, users } from '../../support/users';
import { convertModelAfterMultiPart } from '../../support/utils';

let exam: Exam;

describe('Exam assessment', () => {
    let course: Course;
    let examEnd: Dayjs;
    let programmingAssessmentSuccessful = false;
    let modelingAssessmentSuccessful = false;
    let textAssessmentSuccessful = false;
    let studentOneName: string;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse({ customizeGroups: true }).then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
            courseManagementAPIRequest.addTutorToCourse(course, tutor);
            courseManagementAPIRequest.addInstructorToCourse(course, instructor);
        });
        users.getUserInfo(studentOne.username, (userInfo) => {
            studentOneName = userInfo.name;
        });
    });

    // For some reason the typing of cypress gets slower the longer the test runs, so we test the programming exercise first
    describe('Programming exercise assessment', () => {
        before('Prepare exam', () => {
            examEnd = dayjs().add(2, 'minutes');
            prepareExam(course, examEnd, ExerciseType.PROGRAMMING);
        });

        it('Assess a programming exercise submission (MANUAL)', () => {
            cy.login(instructor);
            examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            cy.login(tutor);
            startAssessing(course.id!, exam.id!, 155000);
            examAssessment.addNewFeedback(2, 'Good job');
            examAssessment.submit();
            cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
            examParticipation.getResultScore().should('contain.text', '66.2%').and('be.visible');
            programmingAssessmentSuccessful = true;
        });

        it('Complaints about programming exercises assessment', () => {
            if (programmingAssessmentSuccessful) {
                handleComplaint(course, exam, false, ExerciseType.PROGRAMMING);
            }
        });
    });

    describe('Modeling exercise assessment', () => {
        before('Prepare exam', () => {
            examEnd = dayjs().add(45, 'seconds');
            prepareExam(course, examEnd, ExerciseType.MODELING);
        });

        it('Assess a modeling exercise submission', () => {
            cy.login(instructor);
            examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            cy.login(tutor);
            startAssessing(course.id!, exam.id!, 60000);
            modelingExerciseAssessment.addNewFeedback(5, 'Good');
            modelingExerciseAssessment.openAssessmentForComponent(1);
            modelingExerciseAssessment.assessComponent(-1, 'Wrong');
            modelingExerciseAssessment.clickNextAssessment();
            modelingExerciseAssessment.assessComponent(0, 'Neutral');
            modelingExerciseAssessment.clickNextAssessment();
            examAssessment.submitModelingAssessment().then((assessmentResponse: Interception) => {
                expect(assessmentResponse.response?.statusCode).to.equal(200);
            });
            cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
            examParticipation.getResultScore().should('contain.text', '40%').and('be.visible');
            modelingAssessmentSuccessful = true;
        });

        it('Complaints about modeling exercises assessment', () => {
            if (modelingAssessmentSuccessful) {
                handleComplaint(course, exam, true, ExerciseType.MODELING);
            }
        });
    });

    describe('Text exercise assessment', () => {
        before('Prepare exam', () => {
            examEnd = dayjs().add(40, 'seconds');
            prepareExam(course, examEnd, ExerciseType.TEXT);
        });

        it('Assess a text exercise submission', () => {
            cy.login(instructor);
            examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            cy.login(tutor);
            startAssessing(course.id!, exam.id!, 60000);
            examAssessment.addNewFeedback(7, 'Good job');
            examAssessment.submitTextAssessment().then((assessmentResponse: Interception) => {
                expect(assessmentResponse.response!.statusCode).to.equal(200);
            });
            cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
            examParticipation.getResultScore().should('contain.text', '70%').and('be.visible');
            textAssessmentSuccessful = true;
        });

        it('Complaints about text exercises assessment', () => {
            if (textAssessmentSuccessful) {
                handleComplaint(course, exam, false, ExerciseType.TEXT);
            }
        });
    });

    describe('Quiz exercise assessment', () => {
        let resultDate: Dayjs;

        before('Prepare exam', () => {
            examEnd = dayjs().add(30, 'seconds');
            resultDate = examEnd.add(5, 'seconds');
            prepareExam(course, examEnd, ExerciseType.QUIZ);
        });

        it('Assesses quiz automatically', () => {
            cy.login(instructor);
            examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            if (dayjs().isBefore(examEnd)) {
                cy.wait(examEnd.diff(dayjs(), 'ms') + 1000);
            }
            examManagement.openAssessmentDashboard(course.id!, exam.id!, 60000);
            cy.visit(`/course-management/${course.id}/exams/${exam.id}/assessment-dashboard`);
            courseAssessment.clickEvaluateQuizzes().its('response.statusCode').should('eq', 200);
            if (dayjs().isBefore(resultDate)) {
                cy.wait(resultDate.diff(dayjs(), 'ms') + 1000);
            }
            examManagement.checkQuizSubmission(course.id!, exam.id!, studentOneName, '50%');
            cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
            examParticipation.getResultScore().should('contain.text', '50%').and('be.visible');
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});

function prepareExam(course: Course, end: dayjs.Dayjs, exerciseType: ExerciseType) {
    cy.login(admin);
    const resultDate = end.add(1, 'second');
    const examConfig: Exam = {
        course,
        startDate: dayjs(),
        endDate: end,
        numberOfCorrectionRoundsInExam: 1,
        examStudentReviewStart: resultDate,
        examStudentReviewEnd: resultDate.add(1, 'minute'),
        publishResultsDate: resultDate,
        gracePeriod: 10,
    };
    examAPIRequests.createExam(examConfig).then((examResponse) => {
        exam = examResponse.body;
        examAPIRequests.registerStudentForExam(exam, studentOne);
        let additionalData = {};
        switch (exerciseType) {
            case ExerciseType.PROGRAMMING:
                additionalData = { submission: javaPartiallySuccessful, progExerciseAssessmentType: ProgrammingExerciseAssessmentType.SEMI_AUTOMATIC };
                break;
            case ExerciseType.TEXT:
                additionalData = { textFixture: 'loremIpsum-short.txt' };
                break;
            case ExerciseType.QUIZ:
                additionalData = { quizExerciseID: 0 };
                break;
        }

        examExerciseGroupCreation.addGroupWithExercise(exam, exerciseType, additionalData).then((response) => {
            examAPIRequests.generateMissingIndividualExams(exam);
            examAPIRequests.prepareExerciseStartForExam(exam);
            makeExamSubmission(course, exam, response);
        });
    });
}

function makeExamSubmission(course: Course, exam: Exam, exercise: Exercise) {
    examParticipation.startParticipation(studentOne, course, exam);
    examNavigation.openExerciseAtIndex(0);
    examParticipation.makeSubmission(exercise.id, exercise.type, exercise.additionalData);
    cy.wait(2000);
    examNavigation.handInEarly();
    examStartEnd.finishExam();
}

function startAssessing(courseID: number, examID: number, timeout: number) {
    examManagement.openAssessmentDashboard(courseID, examID, timeout);
    courseAssessment.clickExerciseDashboardButton();
    exerciseAssessment.clickHaveReadInstructionsButton();
    exerciseAssessment.clickStartNewAssessment();
    exerciseAssessment.getLockedMessage();
}

function handleComplaint(course: Course, exam: Exam, reject: boolean, exerciseType: ExerciseType) {
    const complaintText = 'Lorem ipsum dolor sit amet';
    const complaintResponseText = ' consetetur sadipscing elitr';

    cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
    studentAssessment.startComplaint();
    studentAssessment.enterComplaint(complaintText);
    studentAssessment.submitComplaint();
    cy.get('.message').should('contain.text', 'Your complaint has been submitted');

    cy.login(instructor, '/course-management/' + course.id + '/exams');
    examManagement.openAssessmentDashboard(course.id!, exam.id!);
    courseAssessment.clickExerciseDashboardButton();
    exerciseAssessment.clickHaveReadInstructionsButton();

    exerciseAssessment.clickEvaluateComplaint();
    exerciseAssessment.getComplaintText().should('have.value', complaintText);
    if (reject) {
        examAssessment.rejectComplaint(complaintResponseText, true, exerciseType);
    } else {
        examAssessment.acceptComplaint(complaintResponseText, true, exerciseType);
    }
    if (exerciseType == ExerciseType.MODELING) {
        cy.get('.message').should('contain.text', 'Response to complaint has been submitted');
    } else {
        cy.get('.message').should('contain.text', 'The assessment was updated successfully.');
    }

    cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
    if (reject) {
        studentAssessment.getComplaintBadge().should('contain.text', 'Complaint was rejected');
    } else {
        studentAssessment.getComplaintBadge().should('contain.text', 'Complaint was accepted');
    }
    studentAssessment.getComplaintResponse().should('have.value', complaintResponseText);
}

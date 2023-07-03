import { Interception } from 'cypress/types/net-stubbing';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExamBuilder, ProgrammingExerciseAssessmentType, convertModelAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import partiallySuccessful from '../../fixtures/exercise/programming/partially_successful/submission.json';
import dayjs, { Dayjs } from 'dayjs/esm';
import {
    courseAssessment,
    courseManagementRequest,
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
import { admin, instructor, studentOne, tutor, users } from '../../support/users';
import { EXERCISE_TYPE } from '../../support/constants';
import { Exercise } from '../../support/pageobjects/exam/ExamParticipation';

// This is a workaround for uncaught athene errors. When opening a text submission athene throws an uncaught exception, which fails the test
Cypress.on('uncaught:exception', () => {
    return false;
});

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
        courseManagementRequest.createCourse(true).then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.addTutorToCourse(course, tutor);
            courseManagementRequest.addInstructorToCourse(course, instructor);
        });
        users.getUserInfo(studentOne.username, (userInfo) => {
            studentOneName = userInfo.name;
        });
    });

    // For some reason the typing of cypress gets slower the longer the test runs, so we test the programming exercise first
    describe('Programming exercise assessment', () => {
        before('Prepare exam', () => {
            examEnd = dayjs().add(1, 'minutes');
            prepareExam(course, examEnd, EXERCISE_TYPE.Programming);
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
                handleComplaint(course, exam, false, EXERCISE_TYPE.Programming);
            }
        });
    });

    describe('Modeling exercise assessment', () => {
        before('Prepare exam', () => {
            examEnd = dayjs().add(30, 'seconds');
            prepareExam(course, examEnd, EXERCISE_TYPE.Modeling);
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
                handleComplaint(course, exam, true, EXERCISE_TYPE.Modeling);
            }
        });
    });

    describe('Text exercise assessment', () => {
        before('Prepare exam', () => {
            examEnd = dayjs().add(40, 'seconds');
            prepareExam(course, examEnd, EXERCISE_TYPE.Text);
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
                handleComplaint(course, exam, false, EXERCISE_TYPE.Text);
            }
        });
    });

    describe('Quiz exercise assessment', () => {
        let resultDate: Dayjs;

        before('Prepare exam', () => {
            examEnd = dayjs().add(30, 'seconds');
            resultDate = examEnd.add(5, 'seconds');
            prepareExam(course, examEnd, EXERCISE_TYPE.Quiz);
        });

        it('Assesses quiz automatically', () => {
            cy.login(instructor);
            examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            if (dayjs().isBefore(examEnd)) {
                cy.wait(examEnd.diff(dayjs(), 'ms') + 10000);
            }
            examManagement.openAssessmentDashboard(course.id!, exam.id!, 60000);
            cy.visit(`/course-management/${course.id}/exams/${exam.id}/assessment-dashboard`);
            courseAssessment.clickEvaluateQuizzes().its('response.statusCode').should('eq', 200);
            if (dayjs().isBefore(resultDate)) {
                cy.wait(resultDate.diff(dayjs(), 'ms') + 10000);
            }
            examManagement.checkQuizSubmission(course.id!, exam.id!, studentOneName, '50%');
            cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
            examParticipation.getResultScore().should('contain.text', '50%').and('be.visible');
        });
    });

    after('Delete course', () => {
        courseManagementRequest.deleteCourse(course, admin);
    });
});

function prepareExam(course: Course, end: dayjs.Dayjs, exerciseType: EXERCISE_TYPE) {
    cy.login(admin);
    const resultDate = end.add(1, 'second');
    const examContent = new ExamBuilder(course)
        .visibleDate(dayjs().subtract(1, 'hour'))
        .startDate(dayjs())
        .endDate(end)
        .correctionRounds(1)
        .examStudentReviewStart(resultDate.add(10, 'seconds'))
        .examStudentReviewEnd(resultDate.add(1, 'minute'))
        .publishResultsDate(resultDate)
        .gracePeriod(10)
        .build();
    courseManagementRequest.createExam(examContent).then((examResponse) => {
        exam = examResponse.body;
        courseManagementRequest.registerStudentForExam(exam, studentOne);
        let additionalData = {};
        switch (exerciseType) {
            case EXERCISE_TYPE.Programming:
                additionalData = { submission: partiallySuccessful, progExerciseAssessmentType: ProgrammingExerciseAssessmentType.SEMI_AUTOMATIC };
                break;
            case EXERCISE_TYPE.Text:
                additionalData = { textFixture: 'loremIpsum-short.txt' };
                break;
            case EXERCISE_TYPE.Quiz:
                additionalData = { quizExerciseID: 0 };
                break;
        }

        examExerciseGroupCreation.addGroupWithExercise(exam, exerciseType, additionalData).then((response) => {
            courseManagementRequest.generateMissingIndividualExams(exam);
            courseManagementRequest.prepareExerciseStartForExam(exam);
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

function handleComplaint(course: Course, exam: Exam, reject: boolean, exerciseType: EXERCISE_TYPE) {
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
    if (exerciseType == EXERCISE_TYPE.Modeling) {
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

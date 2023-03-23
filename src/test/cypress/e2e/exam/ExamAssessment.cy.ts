import { Interception } from 'cypress/types/net-stubbing';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exam } from 'app/entities/exam.model';
import { CypressAssessmentType, CypressExamBuilder, convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import partiallySuccessful from '../../fixtures/exercise/programming/partially_successful/submission.json';
import dayjs, { Dayjs } from 'dayjs/esm';
import textSubmission from '../../fixtures/exercise/text/submission.json';
import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import {
    courseAssessment,
    courseManagementRequest,
    examAssessment,
    examManagement,
    examNavigation,
    examStartEnd,
    exerciseAssessment,
    modelingExerciseAssessment,
    modelingExerciseEditor,
    programmingExerciseEditor,
    quizExerciseMultipleChoice,
    studentAssessment,
    textExerciseEditor,
} from '../../support/artemis';
import { admin, instructor, studentOne, tutor } from '../../support/users';
import { EXERCISE_TYPE } from '../../support/constants';

let exam: Exam;
let exerciseGroup: ExerciseGroup;
let course: Course;

// This is a workaround for uncaught athene errors. When opening a text submission athene throws an uncaught exception, which fails the test
Cypress.on('uncaught:exception', () => {
    return false;
});

describe('Exam assessment', () => {
    let examEnd: Dayjs;
    let programmingAssessmentSuccessful = false;
    let modelingAssessmentSuccessful = false;
    let textAssessmentSuccessful = false;

    before('Create a course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.addTutorToCourse(course, tutor);
        });
    });

    // For some reason the typing of cypress gets slower the longer the test runs, so we test the programming exercise first
    describe('Programming exercise assessment', () => {
        before('Prepare exam', () => {
            examEnd = dayjs().add(2.5, 'minutes');
            prepareExam(examEnd);
        });

        before('Create exam, exercise and submission', () => {
            cy.login(instructor);
            courseManagementRequest
                .createProgrammingExercise(
                    { exerciseGroup },
                    undefined,
                    false,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    CypressAssessmentType.SEMI_AUTOMATIC,
                )
                .then((progResponse) => {
                    const programmingExercise = progResponse.body;
                    courseManagementRequest.generateMissingIndividualExams(exam);
                    courseManagementRequest.prepareExerciseStartForExam(exam);
                    cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
                    examStartEnd.startExam();
                    examNavigation.openExerciseAtIndex(0);
                    programmingExerciseEditor.makeSubmissionAndVerifyResults(programmingExercise.id!, programmingExercise.packageName!, partiallySuccessful, () => {
                        examNavigation.handInEarly();
                        examStartEnd.finishExam();
                    });
                });
        });

        it('Assess a programming exercise submission (MANUAL)', () => {
            cy.login(tutor, '/course-management/' + course.id + '/exams');
            examManagement.openAssessmentDashboard(exam.id!, 155000);
            startAssessing();
            examAssessment.addNewFeedback(2, 'Good job');
            examAssessment.submit();
            cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
            programmingExerciseEditor.getResultScore().should('contain.text', '66.2%, 6 of 13 passed, 6.6 points').and('be.visible');
            programmingAssessmentSuccessful = true;
        });

        it('Complaints about programming exercises assessment', () => {
            if (programmingAssessmentSuccessful) {
                handleComplaint(false, EXERCISE_TYPE.Programming);
            }
        });
    });

    describe('Modeling exercise assessment', () => {
        before('Prepare exam', () => {
            examEnd = dayjs().add(20, 'seconds');
            prepareExam(examEnd);
        });

        before('Create modeling exercise and submission', () => {
            cy.login(instructor);
            courseManagementRequest.createModelingExercise({ exerciseGroup }).then((response) => {
                const exercise = response.body;
                courseManagementRequest.generateMissingIndividualExams(exam);
                courseManagementRequest.prepareExerciseStartForExam(exam);
                cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
                examStartEnd.startExam();
                examNavigation.openExerciseAtIndex(0);
                modelingExerciseEditor.addComponentToModel(exercise.id!, 1);
                modelingExerciseEditor.addComponentToModel(exercise.id!, 2);
                modelingExerciseEditor.addComponentToModel(exercise.id!, 3);
                examNavigation.handInEarly();
                examStartEnd.finishExam();
            });
        });

        it('Assess a modeling exercise submission', () => {
            cy.login(tutor, '/course-management/' + course.id + '/exams');
            examManagement.openAssessmentDashboard(exam.id!, 60000);
            startAssessing();
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
            programmingExerciseEditor.getResultScore().should('contain.text', '40%, 4 points').and('be.visible');
            modelingAssessmentSuccessful = true;
        });

        it('Complaints about modeling exercises assessment', () => {
            if (modelingAssessmentSuccessful) {
                handleComplaint(true, EXERCISE_TYPE.Modeling);
            }
        });
    });

    describe('Text exercise assessment', () => {
        before('Prepare exam', () => {
            examEnd = dayjs().add(25, 'seconds');
            prepareExam(examEnd);
        });

        before('Create text exercise and submission', () => {
            cy.login(instructor);
            const exerciseTitle = 'Cypress Text Exercise';
            courseManagementRequest.createTextExercise({ exerciseGroup }, exerciseTitle).then((response) => {
                const exercise = response.body;
                courseManagementRequest.generateMissingIndividualExams(exam);
                courseManagementRequest.prepareExerciseStartForExam(exam);
                cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
                examStartEnd.startExam();
                examNavigation.openExerciseAtIndex(0);
                textExerciseEditor.typeSubmission(exercise.id, textSubmission.text);
                textExerciseEditor.saveAndContinue().then((submissionResponse) => {
                    expect(submissionResponse.response?.statusCode).to.equal(200);
                });
                examNavigation.handInEarly();
                examStartEnd.finishExam();
            });
        });

        it('Assess a text exercise submission', () => {
            cy.login(tutor, '/course-management/' + course.id + '/exams');
            examManagement.openAssessmentDashboard(exam.id!, 60000);
            startAssessing();
            examAssessment.addNewFeedback(7, 'Good job');
            examAssessment.submitTextAssessment().then((assessmentResponse: Interception) => {
                expect(assessmentResponse.response!.statusCode).to.equal(200);
            });
            cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
            programmingExerciseEditor.getResultScore().should('contain.text', '70%, 7 points').and('be.visible');
            textAssessmentSuccessful = true;
        });

        it('Complaints about text exercises assessment', () => {
            if (textAssessmentSuccessful) {
                handleComplaint(false, EXERCISE_TYPE.Text);
            }
        });
    });

    describe('Quiz exercise assessment', () => {
        let resultDate: Dayjs;

        before('Prepare exam', () => {
            examEnd = dayjs().add(25, 'seconds');
            resultDate = examEnd.add(5, 'seconds');
            prepareExam(examEnd, resultDate);
        });

        before('Create exercise and submission', () => {
            cy.login(instructor);
            courseManagementRequest.createQuizExercise({ exerciseGroup }, [multipleChoiceQuizTemplate], 'Cypress Quiz').then((quizResponse) => {
                const exercise = quizResponse.body;
                courseManagementRequest.generateMissingIndividualExams(exam);
                courseManagementRequest.prepareExerciseStartForExam(exam);
                cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
                examStartEnd.startExam();
                examNavigation.openExerciseAtIndex(0);
                quizExerciseMultipleChoice.tickAnswerOption(exercise.id, 0, quizResponse.body.quizQuestions[0].id);
                quizExerciseMultipleChoice.tickAnswerOption(exercise.id, 2, quizResponse.body.quizQuestions[0].id);
                examNavigation.handInEarly();
                examStartEnd.finishExam();
            });
        });

        it('Assesses quiz automatically', () => {
            if (dayjs().isBefore(examEnd)) {
                cy.wait(examEnd.diff(dayjs(), 'ms') + 5000);
            }
            cy.login(admin, `/course-management/${course.id}/exams/${exam.id}/assessment-dashboard`);
            courseAssessment.clickEvaluateQuizzes().its('response.statusCode').should('eq', 200);
            if (dayjs().isBefore(resultDate)) {
                cy.wait(examEnd.diff(dayjs(), 'ms'));
            }
            cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
            // Sometimes the feedback fails to load properly on the first load...
            const resultSelector = '#result-score';
            cy.reloadUntilFound(resultSelector);
            programmingExerciseEditor.getResultScore().should('contain.text', '50%, 5 points').and('be.visible');
        });
    });

    after('Delete course', () => {
        cy.login(admin);
        courseManagementRequest.deleteCourse(course.id!);
    });
});

function startAssessing() {
    courseAssessment.clickExerciseDashboardButton();
    exerciseAssessment.clickHaveReadInstructionsButton();
    exerciseAssessment.clickStartNewAssessment();
    cy.get('#assessmentLockedCurrentUser').should('be.visible');
}

function prepareExam(end: dayjs.Dayjs, resultDate = end.add(1, 'seconds')) {
    cy.login(admin);
    const examContent = new CypressExamBuilder(course)
        .visibleDate(dayjs().subtract(1, 'hour'))
        .startDate(dayjs())
        .endDate(end)
        .correctionRounds(1)
        .examStudentReviewStart(resultDate.add(10, 'seconds'))
        .examStudentReviewEnd(resultDate.add(1, 'minute'))
        .publishResultsDate(resultDate)
        .gracePeriod(0)
        .build();
    courseManagementRequest.createExam(examContent).then((examResponse) => {
        exam = examResponse.body;
        courseManagementRequest.registerStudentForExam(exam, studentOne);
        courseManagementRequest.addExerciseGroupForExam(exam).then((groupResponse) => {
            exerciseGroup = groupResponse.body;
        });
    });
}

function handleComplaint(reject: boolean, exerciseType: EXERCISE_TYPE) {
    const complaintText = 'Lorem ipsum dolor sit amet';
    const complaintResponseText = ' consetetur sadipscing elitr';

    cy.login(studentOne, '/courses/' + course.id + '/exams/' + exam.id);
    studentAssessment.startComplaint();
    studentAssessment.enterComplaint(complaintText);
    studentAssessment.submitComplaint();
    cy.get('.message').should('contain.text', 'Your complaint has been submitted');

    cy.login(instructor, '/course-management/' + course.id + '/exams');
    examManagement.openAssessmentDashboard(exam.id!, 60000);
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

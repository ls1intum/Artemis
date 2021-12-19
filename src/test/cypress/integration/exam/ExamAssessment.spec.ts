import { artemis } from '../../support/ArtemisTesting';
import { CypressAssessmentType, CypressExamBuilder } from '../../support/requests/CourseManagementRequests';
import partiallySuccessful from '../../fixtures/programming_exercise_submissions/partially_successful/submission.json';
import dayjs, { Dayjs } from 'dayjs';
import textSubmission from '../../fixtures/text_exercise_submission/text_exercise_submission.json';
import multipleChoiceQuizTemplate from '../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';
import { makeSubmissionAndVerifyResults } from '../../support/pageobjects/exercises/programming/OnlineEditorPage';

// requests
const courseManagementRequests = artemis.requests.courseManagement;

// page objects
const assessmentDashboard = artemis.pageobjects.assessment.course;
const examStartEnd = artemis.pageobjects.examStartEnd;
const modelingEditor = artemis.pageobjects.modelingExercise.editor;
const modelingAssessment = artemis.pageobjects.modelingExercise.assessmentEditor;
const editorPage = artemis.pageobjects.programmingExercise.editor;
const examAssessment = artemis.pageobjects.assessment.exam;
const examNavigation = artemis.pageobjects.examNavigationBar;
const textEditor = artemis.pageobjects.textExercise.editor;
const exerciseAssessment = artemis.pageobjects.assessment.exercise;
const multipleChoice = artemis.pageobjects.quizExercise.multipleChoice;

// Common primitives
const admin = artemis.users.getAdmin();
const student = artemis.users.getStudentOne();
const tutor = artemis.users.getTutor();
let exam: any;
let exerciseGroup: any;
let course: any;

// This is a workaround for uncaught athene errors. When opening a text submission athene throws an uncaught exception, which fails the test
Cypress.on('uncaught:exception', () => {
    return false;
});

describe('Exam assessment', () => {
    let examEnd: Dayjs;

    before('Create a course', () => {
        cy.login(admin);
        courseManagementRequests.createCourse(true).then((response) => {
            course = response.body;
            courseManagementRequests.addStudentToCourse(course.id, artemis.users.getStudentOne().username);
            courseManagementRequests.addTutorToCourse(course, artemis.users.getTutor());
        });
    });

    afterEach('Delete exam', () => {
        cy.login(admin);
        courseManagementRequests.deleteExam(exam);
    });

    after('Delete course', () => {
        cy.login(admin);
        courseManagementRequests.deleteCourse(course.id);
    });

    // For some reason the typing of cypress gets slower the longer the test runs, so we test the programming exercise first
    describe('Exam programming exercise assessment', () => {
        const examDuration = 155000;

        before('Prepare exam', () => {
            examEnd = dayjs().add(examDuration, 'milliseconds');
            prepareExam(examEnd);
        });

        beforeEach('Create exam, exercise and submission', () => {
            courseManagementRequests
                .createProgrammingExercise({ exerciseGroup }, undefined, undefined, undefined, undefined, undefined, undefined, undefined, CypressAssessmentType.SEMI_AUTOMATIC)
                .then((progRespone) => {
                    const programmingExercise = progRespone.body;
                    courseManagementRequests.generateMissingIndividualExams(exam);
                    courseManagementRequests.prepareExerciseStartForExam(exam);
                    cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                    examStartEnd.startExam();
                    cy.contains(programmingExercise.title).should('be.visible').click();
                    makeSubmissionAndVerifyResults(editorPage, programmingExercise.packageName, partiallySuccessful, () => {
                        examNavigation.handInEarly();
                        examStartEnd.finishExam();
                    });
                });
        });

        it('Assess a programming exercise submission (MANUAL)', () => {
            cy.login(tutor, '/course-management/' + course.id + '/exams');
            cy.contains('Assessment Dashboard', { timeout: examDuration }).click();
            startAssessing();
            examAssessment.addNewFeedback(2, 'Good job');
            examAssessment.submit();
            cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
            cy.get('.question-options').contains('6.6 of 10 points').should('be.visible');
        });
    });

    describe('Exam exercise assessment', () => {
        beforeEach('Generate new exam name', () => {
            examEnd = dayjs().add(45, 'seconds');
            prepareExam(examEnd);
        });

        describe('Modeling exercise assessment', () => {
            beforeEach('Create exercise and submission', () => {
                courseManagementRequests.createModelingExercise({ exerciseGroup }).then((modelingResponse) => {
                    courseManagementRequests.generateMissingIndividualExams(exam);
                    courseManagementRequests.prepareExerciseStartForExam(exam);
                    cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                    examStartEnd.startExam();
                    cy.contains(modelingResponse.body.title).should('be.visible').click();
                    modelingEditor.addComponentToModel(1);
                    modelingEditor.addComponentToModel(2);
                    modelingEditor.addComponentToModel(3);
                    examNavigation.handInEarly();
                    examStartEnd.finishExam();
                });
            });

            it('Assess a modeling exercise submission', () => {
                cy.login(tutor, '/course-management/' + course.id + '/exams');
                cy.contains('Assessment Dashboard', { timeout: 60000 }).click();
                startAssessing();
                modelingAssessment.addNewFeedback(5, 'Good');
                modelingAssessment.openAssessmentForComponent(1);
                modelingAssessment.assessComponent(-1, 'Wrong');
                modelingAssessment.clickNextAssessment();
                modelingAssessment.assessComponent(0, 'Neutral');
                modelingAssessment.clickNextAssessment();
                examAssessment.submitModelingAssessment().then((assessmentResponse) => {
                    expect(assessmentResponse.response?.statusCode).to.equal(200);
                });
                cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                cy.contains('4 of 10 points').should('be.visible');
            });
        });

        describe('Text exercise assessment', () => {
            beforeEach('Create exercise and submission', () => {
                const exerciseTitle = 'Cypress Text Exercise';
                courseManagementRequests.createTextExercise({ exerciseGroup }, exerciseTitle);
                courseManagementRequests.generateMissingIndividualExams(exam);
                courseManagementRequests.prepareExerciseStartForExam(exam);
                cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                examStartEnd.startExam();
                cy.contains(exerciseTitle).click();
                textEditor.typeSubmission(textSubmission.text);
                textEditor.submit().then((submissionResponse) => {
                    expect(submissionResponse.response?.statusCode).to.equal(200);
                });
                examNavigation.handInEarly();
                examStartEnd.finishExam();
            });

            it('Assess a text exercise submission', () => {
                cy.login(tutor, '/course-management/' + course.id + '/exams');
                cy.contains('Assessment Dashboard', { timeout: 60000 }).click();
                startAssessing();
                examAssessment.addNewFeedback(7, 'Good job');
                examAssessment.submitTextAssessment().then((assessmentResponse) => {
                    expect(assessmentResponse.response?.statusCode).to.equal(200);
                });
                cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                cy.get('.question-options').contains('7 of 10 points').should('be.visible');
            });
        });
    });

    describe('Assess a quiz exercise submission', () => {
        let resultDate: Dayjs;

        beforeEach('Generate new exam name', () => {
            examEnd = dayjs().add(25, 'seconds');
            resultDate = examEnd.add(5, 'seconds');
            prepareExam(examEnd, resultDate);
        });

        beforeEach('Create exercise and submission', () => {
            courseManagementRequests.createQuizExercise({ exerciseGroup }, [multipleChoiceQuizTemplate], 'Cypress Quiz').then((quizResponse) => {
                courseManagementRequests.generateMissingIndividualExams(exam);
                courseManagementRequests.prepareExerciseStartForExam(exam);
                cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                examStartEnd.startExam();
                cy.contains('Cypress Quiz').click();
                multipleChoice.tickAnswerOption(0, quizResponse.body.quizQuestions[0].id);
                multipleChoice.tickAnswerOption(2, quizResponse.body.quizQuestions[0].id);
                examNavigation.handInEarly();
                examStartEnd.finishExam();
            });
        });

        it('Assesses quiz automatically', () => {
            if (dayjs().isBefore(examEnd)) {
                cy.wait(examEnd.diff(dayjs(), 'ms'));
            }
            cy.login(admin, `/course-management/${course.id}/exams/${exam.id}/assessment-dashboard`);
            assessmentDashboard.clickEvaluateQuizzes().its('response.statusCode').should('eq', 200);
            if (dayjs().isBefore(resultDate)) {
                cy.wait(examEnd.diff(dayjs(), 'ms'));
            }
            cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
            const score = '5 of 10 points';
            // Sometimes the feedback fails to load properly on the first load...
            cy.reloadUntilFound(`jhi-result:contains(${score})`);
            cy.get('jhi-result').contains(score).should('be.visible');
        });
    });

    function startAssessing() {
        artemis.pageobjects.assessment.course.clickExerciseDashboardButton();
        exerciseAssessment.clickHaveReadInstructionsButton();
        exerciseAssessment.clickStartNewAssessment();
        cy.contains('You have the lock for this assessment').should('be.visible');
    }

    function prepareExam(end: dayjs.Dayjs, resultDate = end.add(1, 'seconds')) {
        cy.login(admin);
        const examContent = new CypressExamBuilder(course)
            .visibleDate(dayjs().subtract(1, 'day'))
            .startDate(dayjs())
            .endDate(end)
            .publishResultsDate(resultDate)
            .gracePeriod(0)
            .build();
        courseManagementRequests.createExam(examContent).then((examResponse) => {
            exam = examResponse.body;
            courseManagementRequests.registerStudentForExam(exam, student);
            courseManagementRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                exerciseGroup = groupResponse.body;
            });
        });
    }
});

import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Exam } from 'app/entities/exam.model';
import { GET, BASE_API } from '../../support/constants';
import { CypressExamBuilder } from '../../support/requests/CourseManagementRequests';
import { artemis } from '../../support/ArtemisTesting';
import dayjs from 'dayjs/esm';
import submission from '../../fixtures/programming_exercise_submissions/all_successful/submission.json';
import multipleChoiceTemplate from '../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';
import { Course } from 'app/entities/course.model';
import { Interception } from 'cypress/types/net-stubbing';

// Requests
const courseRequests = artemis.requests.courseManagement;

// User management
const users = artemis.users;
const student = users.getStudentOne();

// Pageobjects
const courses = artemis.pageobjects.course.list;
const courseOverview = artemis.pageobjects.course.overview;
const examStartEnd = artemis.pageobjects.exam.startEnd;
const examNavigation = artemis.pageobjects.exam.navigationBar;
const onlineEditor = artemis.pageobjects.exercise.programming.editor;
const modelingEditor = artemis.pageobjects.exercise.modeling.editor;
const multipleChoiceQuiz = artemis.pageobjects.exercise.quiz.multipleChoice;

// Common primitives
const textExerciseTitle = 'Cypress text exercise';
const packageName = 'de.test';

describe('Exam participation', () => {
    let course: Course;
    let exam: Exam;
    let quizExercise: QuizExercise;

    before(() => {
        cy.login(users.getAdmin());
        courseRequests.createCourse(true).then((response) => {
            course = response.body;
            const examContent = new CypressExamBuilder(course)
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .maxPoints(40)
                .numberOfExercises(4)
                .build();
            courseRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                courseRequests.registerStudentForExam(exam, student);
                courseRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                    courseRequests.createTextExercise({ exerciseGroup: groupResponse.body }, textExerciseTitle);
                });
                courseRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                    courseRequests.createProgrammingExercise({ exerciseGroup: groupResponse.body }, undefined, false, undefined, undefined, undefined, undefined, packageName);
                });
                courseRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                    courseRequests.createModelingExercise({ exerciseGroup: groupResponse.body });
                });
                courseRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                    courseRequests.createQuizExercise({ exerciseGroup: groupResponse.body }, [multipleChoiceTemplate]).then((quizResponse) => {
                        quizExercise = quizResponse.body;
                    });
                });
                courseRequests.generateMissingIndividualExams(exam);
                courseRequests.prepareExerciseStartForExam(exam);
            });
        });
    });

    it('Participates as a student in a registered exam', () => {
        startParticipation();
        openTextExercise();
        makeTextExerciseSubmission();
        makeProgrammingExerciseSubmission();
        openModelingExercise();
        makeModelingExerciseSubmission();
        openQuizExercise();
        makeQuizExerciseSubmission();

        handInEarly();
        verifyFinalPage();
    });

    function startParticipation() {
        cy.login(student, '/');
        courses.openCourse(course.id!);
        courseOverview.openExamsTab();
        courseOverview.openExam(exam.id!);
        cy.url().should('contain', `/exams/${exam.id}`);
        examStartEnd.startExam();
    }

    function openTextExercise() {
        examNavigation.openExerciseAtIndex(0);
    }

    function openModelingExercise() {
        examNavigation.openExerciseAtIndex(2);
    }

    function openQuizExercise() {
        examNavigation.openExerciseAtIndex(3);
    }

    function makeTextExerciseSubmission() {
        const textEditor = artemis.pageobjects.exercise.text.editor;
        cy.fixture('loremIpsum.txt').then((submissionText) => {
            textEditor.typeSubmission(submissionText);
            // Loading the content of the existing files might take some time so we wait for the return of the request here
            cy.intercept(GET, BASE_API + 'repository/*/files').as('getFiles');
            textEditor.saveAndContinue().its('request.body.text').should('eq', submissionText);
            cy.wait('@getFiles').its('response.statusCode').should('eq', 200);
        });
    }

    function makeProgrammingExerciseSubmission() {
        onlineEditor.toggleCompressFileTree();
        onlineEditor.deleteFile('Client.java');
        onlineEditor.deleteFile('BubbleSort.java');
        onlineEditor.deleteFile('MergeSort.java');
        onlineEditor.typeSubmission(submission, 'de.test');
        onlineEditor.submit();
        onlineEditor.getResultScore().contains('100%').and('be.visible');
    }

    function makeModelingExerciseSubmission() {
        modelingEditor.addComponentToModel(1, false);
        modelingEditor.addComponentToModel(2, false);
        modelingEditor.addComponentToModel(3, false);
    }

    function makeQuizExerciseSubmission() {
        multipleChoiceQuiz.tickAnswerOption(0, quizExercise.quizQuestions![0].id);
        multipleChoiceQuiz.tickAnswerOption(2, quizExercise.quizQuestions![0].id);
    }

    function handInEarly() {
        examNavigation.handInEarly();
        examStartEnd.finishExam().then((request: Interception) => {
            expect(request.response!.statusCode).to.eq(200);
        });
    }

    function verifyFinalPage() {
        cy.contains(textExerciseTitle).should('be.visible');
        cy.fixture('loremIpsum.txt').then((submissionText) => {
            cy.contains(submissionText).should('be.visible');
        });
    }

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseRequests.deleteCourse(course.id!);
        }
    });
});

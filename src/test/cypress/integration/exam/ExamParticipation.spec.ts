import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Exam } from 'app/entities/exam.model';
import { GET, BASE_API } from '../../support/constants';
import { CypressExamBuilder } from '../../support/requests/CourseManagementRequests';
import { artemis } from '../../support/ArtemisTesting';
import dayjs from 'dayjs/esm';
import successfulSubmission from '../../fixtures/programming_exercise_submissions/all_successful/submission.json';
import partialSuccessfulSubmission from '../../fixtures/programming_exercise_submissions/partially_successful/submission.json';
import buildErrorSubmission from '../../fixtures/programming_exercise_submissions/build_error/submission.json';
import multipleChoiceTemplate from '../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';
import { Course } from 'app/entities/course.model';
import { Interception } from 'cypress/types/net-stubbing';
import { CypressCredentials } from '../../support/users';

// Requests
const courseRequests = artemis.requests.courseManagement;

// User management
const users = artemis.users;
const studentOne = users.getStudentOne();
const studentTwo = users.getStudentTwo();
const studentThree = users.getStudentThree();

// Pageobjects
const courses = artemis.pageobjects.course.list;
const courseOverview = artemis.pageobjects.course.overview;
const examStartEnd = artemis.pageobjects.exam.startEnd;
const examNavigation = artemis.pageobjects.exam.navigationBar;
const textEditor = artemis.pageobjects.exercise.text.editor;
const onlineEditor = artemis.pageobjects.exercise.programming.editor;
const modelingEditor = artemis.pageobjects.exercise.modeling.editor;
const multipleChoiceQuiz = artemis.pageobjects.exercise.quiz.multipleChoice;
const fileUpload = artemis.pageobjects.exercise.fileUpload.editor;

// Common primitives
const textExerciseTitle = 'Cypress text exercise';
const fileUploadExerciseTitle = 'Cypress file upload exercise';
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
                .maxPoints(50)
                .numberOfExercises(5)
                .build();
            courseRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                courseRequests.registerStudentForExam(exam, studentOne);
                courseRequests.registerStudentForExam(exam, studentTwo);
                courseRequests.registerStudentForExam(exam, studentThree);
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
                courseRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                    courseRequests.createFileUploadExercise({ exerciseGroup: groupResponse.body }, fileUploadExerciseTitle);
                });
                courseRequests.generateMissingIndividualExams(exam);
                courseRequests.prepareExerciseStartForExam(exam);
            });
        });
    });

    describe('Student participations', () => {
        it('Participates as student one in a registered exam', () => {
            startParticipation(studentOne);
            openTextExercise();
            makeTextExerciseSubmission();
            makeProgrammingExerciseSubmission(successfulSubmission, '100%', '13 of 13 passed');
            openModelingExercise();
            makeModelingExerciseSubmission([1, 2, 3]);
            openQuizExercise();
            makeQuizExerciseSubmission([0, 2]);
            openFileUploadExercise();
            makeFileUploadExerciseSubmission();

            handInEarly();
            verifyFinalPage();
        });

        it('Participates as student two in a registered exam', () => {
            startParticipation(studentTwo);
            openTextExercise();
            makeTextExerciseSubmission();
            makeProgrammingExerciseSubmission(partialSuccessfulSubmission, '46.2%', '6 of 13 passed');
            openModelingExercise();
            makeModelingExerciseSubmission([1, 1, 3]);
            openQuizExercise();
            makeQuizExerciseSubmission([1, 3]);
            openFileUploadExercise();
            makeFileUploadExerciseSubmission();

            handInEarly();
            verifyFinalPage();
        });

        it('Participates as student three in a registered exam', () => {
            startParticipation(studentThree);
            openTextExercise();
            makeTextExerciseSubmission();
            makeProgrammingExerciseSubmission(buildErrorSubmission, '0%', 'Build failed');
            openModelingExercise();
            makeModelingExerciseSubmission([3, 3, 3]);
            openQuizExercise();
            makeQuizExerciseSubmission([0, 1, 3]);
            openFileUploadExercise();
            makeFileUploadExerciseSubmission();

            handInEarly();
            verifyFinalPage();
        });
    });

    function startParticipation(student: CypressCredentials) {
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

    function openFileUploadExercise() {
        examNavigation.openExerciseAtIndex(4);
    }

    function makeTextExerciseSubmission() {
        cy.fixture('loremIpsum.txt').then((submissionText) => {
            textEditor.typeSubmission(submissionText);
            // Loading the content of the existing files might take some time so we wait for the return of the request here
            cy.intercept(GET, BASE_API + 'repository/*/files').as('getFiles');
            textEditor.saveAndContinue().its('request.body.text').should('eq', submissionText);
            cy.wait('@getFiles').its('response.statusCode').should('eq', 200);
        });
    }

    function makeProgrammingExerciseSubmission(submission: any, result: string, passed: string) {
        onlineEditor.toggleCompressFileTree();
        onlineEditor.deleteFile('Client.java');
        onlineEditor.deleteFile('BubbleSort.java');
        onlineEditor.deleteFile('MergeSort.java');
        onlineEditor.typeSubmission(submission, 'de.test');
        onlineEditor.submit();
        onlineEditor.getResultScore().contains(result).should('be.visible');
        onlineEditor.getResultScore().contains(passed).should('be.visible');
    }

    function makeModelingExerciseSubmission(components: Array<number>) {
        for (const component of components) {
            modelingEditor.addComponentToModel(component, false);
        }
    }

    function makeQuizExerciseSubmission(options: Array<number>) {
        for (const option of options) {
            multipleChoiceQuiz.tickAnswerOption(option, quizExercise.quizQuestions![0].id);
        }
    }

    function makeFileUploadExerciseSubmission() {
        fileUpload.attachFileExam('pdf-test-file.pdf');
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

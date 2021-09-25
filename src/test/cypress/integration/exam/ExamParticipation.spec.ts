import { GET, BASE_API } from '../../support/constants';
import { CypressExamBuilder } from '../../support/requests/CourseManagementRequests';
import { artemis } from '../../support/ArtemisTesting';
import dayjs from 'dayjs';
import submission from '../../fixtures/programming_exercise_submissions/all_successful/submission.json';
import multipleChoiceTemplate from '../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';

// Requests
const courseRequests = artemis.requests.courseManagement;

// User management
const users = artemis.users;
const student = users.getStudentOne();

// Pageobjects
const courses = artemis.pageobjects.courses;
const courseOverview = artemis.pageobjects.courseOverview;
const examStartEnd = artemis.pageobjects.examStartEnd;
const examNavigation = artemis.pageobjects.examNavigationBar;
const onlineEditor = artemis.pageobjects.programmingExercise.editor;
const modelingEditor = artemis.pageobjects.modelingExercise.editor;
const multipleChoiceQuiz = artemis.pageobjects.quizExercise.multipleChoice;

// Common primitives
const textExerciseTitle = 'Cypress text exercise';

describe('Exam participation', () => {
    let course: any;
    let exam: any;

    before(() => {
        cy.login(users.getAdmin());
        courseRequests.createCourse().then((response) => {
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
                    courseRequests.createProgrammingExercise({ exerciseGroup: groupResponse.body });
                });
                courseRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                    courseRequests.createModelingExercise({ exerciseGroup: groupResponse.body });
                });
                courseRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                    courseRequests.createQuizExercise({ exerciseGroup: groupResponse.body }, [multipleChoiceTemplate]);
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
        courses.openCourse(course.title);
        courseOverview.openExamsTab();
        courseOverview.openExam(exam.title);
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
        const textEditor = artemis.pageobjects.textExercise.editor;
        cy.fixture('loremIpsum.txt').then((submissionText) => {
            textEditor.typeSubmission(submissionText);
            // Loading the content of the existing files might take some time so we wait for the return of the request here
            cy.intercept(GET, BASE_API + 'repository/*/files').as('getFiles');
            textEditor.saveAndContinue().its('request.body.text').should('eq', submissionText);
            cy.wait('@getFiles').its('response.statusCode').should('eq', 200);
        });
    }

    function makeProgrammingExerciseSubmission() {
        onlineEditor.createFileInRootPackage('placeholderFile');
        onlineEditor.deleteFile('Client.java');
        onlineEditor.deleteFile('BubbleSort.java');
        onlineEditor.deleteFile('MergeSort.java');
        onlineEditor.typeSubmission(submission, 'de.test');
        onlineEditor.submit();
        onlineEditor.getResultPanel().contains('100%').should('be.visible');
        onlineEditor.getResultPanel().contains('13 of 13 passed').should('be.visible');
        onlineEditor.getBuildOutput().contains('No build results available').should('be.visible');
        onlineEditor.getInstructionSymbols().each(($el) => {
            cy.wrap($el).find('[data-icon="check"]').should('be.visible');
        });
    }

    function makeModelingExerciseSubmission() {
        modelingEditor.addComponentToModel(1);
        modelingEditor.addComponentToModel(2);
        modelingEditor.addComponentToModel(3);
        modelingEditor.submit();
    }

    function makeQuizExerciseSubmission() {
        multipleChoiceQuiz.tickAnswerOption(0);
        multipleChoiceQuiz.tickAnswerOption(2);
    }

    function handInEarly() {
        examNavigation.handInEarly();
        examStartEnd.finishExam().its('response.statusCode').should('eq', 200);
    }

    function verifyFinalPage() {
        cy.get('.alert').contains('Your exam was submitted successfully.');
        cy.contains(textExerciseTitle).should('be.visible');
        cy.fixture('loremIpsum.txt').then((submissionText) => {
            cy.contains(submissionText).should('be.visible');
        });
    }

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseRequests.deleteCourse(course.id);
        }
    });
});

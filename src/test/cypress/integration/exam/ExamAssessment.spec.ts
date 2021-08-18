import { generateUUID } from '../../support/utils';
import { artemis } from '../../support/ArtemisTesting';
import { CypressExamBuilder } from '../../support/requests/CourseManagementRequests';
import modelingExerciseTemplate from '../../fixtures/requests/modelingExercise_template.json';
import dayjs from 'dayjs';
import { ProgrammingExerciseSubmission } from '../../support/pageobjects/OnlineEditorPage';
import partiallySuccessful from '../../fixtures/programming_exercise_submissions/partially_successful/submission.json';

// requests
const courseManagementRequests = artemis.requests.courseManagement;

// page objects
const examStartEnd = artemis.pageobjects.examStartEnd;
const modelingEditor = artemis.pageobjects.modelingEditor;
const modelingAssessment = artemis.pageobjects.modelingExerciseAssessmentEditor;
const editorPage = artemis.pageobjects.onlineEditor;

// Common primitives
let uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;
const student = artemis.users.getStudentOne();
const tutor = artemis.users.getTutor();
const packageName = 'de.test';

describe('Exam Assessment', () => {
    let course: any;
    let examTitle: string;
    let exam: any;
    let exerciseGroup: any;

    before('Create a course', () => {
        cy.login(artemis.users.getAdmin());
        courseManagementRequests.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
            courseManagementRequests.addStudentToCourse(course.id, artemis.users.getStudentOne().username);
            courseManagementRequests.addTutorToCourse(course, artemis.users.getTutor());
        });
    });

    beforeEach('Generate new exam Name', () => {
        examTitle = 'exam' + generateUUID();
        cy.login(artemis.users.getAdmin());
    });

    after('Delete Course', () => {
        cy.login(artemis.users.getAdmin());
        courseManagementRequests.deleteCourse(course.id);
    });

    describe('Exam exercise Assessment', () => {

        beforeEach('Create Exam', () => {
            cy.login(artemis.users.getAdmin());
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(3, 'hours'))
                .endDate(dayjs().subtract(2, 'hours').add(20, 'seconds')) // //.add(15, 'seconds'))
                .publishResultsDate(dayjs().subtract(2, 'hours').add(30, 'seconds'))
                .gracePeriod(1)
                .build();
            courseManagementRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                courseManagementRequests.registerStudentForExam(course, exam, student);
                courseManagementRequests.addExerciseGroupForExam(course, exam, 'group 1', true).then((groupResponse) => {
                    exerciseGroup = groupResponse.body;
                });
            });
        });

        afterEach('Delete Exam', () => {
            cy.login(artemis.users.getAdmin());
            courseManagementRequests.deleteExam(course, exam);
        });

        it('assess a modeling exercise submission', () => {
            courseManagementRequests.createModelingExercise(modelingExerciseTemplate, null, exerciseGroup).then((modelingResponse) => {
                const modelingExercise = modelingResponse.body;
                courseManagementRequests.generateMissingIndividualExams(course, exam);
                courseManagementRequests.prepareExerciseStartForExam(course, exam);
                // TODO: in the future this might become redundant and should be replaced with requests handling the submission creation
                cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                examStartEnd.startExam();
                cy.contains('Cypress Modeling Exercise').should('be.visible').click();
                modelingEditor.addComponentToModel(1);
                modelingEditor.addComponentToModel(2);
                modelingEditor.addComponentToModel(3);
                cy.intercept('PUT', '/api/exercises/' + modelingExercise.id + '/modeling-submissions').as('createModelingSubmission');
                cy.contains('Save').click();
                cy.wait('@createModelingSubmission');
                cy.contains('Hand in early').click();
                examStartEnd.finishExam();
                //
                cy.login(tutor, '/course-management/' + course.id + '/exams');
                cy.contains('Assessment Dashboard').click();
                cy.get('.btn').contains('Exercise Dashboard').should('be.visible').click();
                cy.contains('I have read and understood the instructions').click();
                cy.get('.btn').contains('Start new assessment').should('be.visible').click();
                cy.contains('You have the lock for this assessment').should('be.visible');
                modelingAssessment.addNewFeedback(2, 'Noice');
                modelingAssessment.openAssessmentForComponent(1);
                modelingAssessment.assessComponent(1, 'Good');
                modelingAssessment.openAssessmentForComponent(2);
                modelingAssessment.assessComponent(0, 'Neutral');
                modelingAssessment.openAssessmentForComponent(3);
                modelingAssessment.assessComponent(-1, 'Wrong');
                cy.contains('Submit').click();
                cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                cy.get('.question-options').contains('2 of 10 points').should('be.visible');
            });
        });
    });

    describe('Exam Programming Exercise Assessment', () => {
        let programmingExerciseName: string;
        let programmingExerciseShortName: string;

        before('Generate exercise names', () => {
            uid = generateUUID();
            programmingExerciseName = 'Cypress programming exercise ' + uid;
            programmingExerciseShortName = 'cypress' + uid;
            // course synchronization
            cy.wait(60000);
        });

        beforeEach('Create Exam', () => {
            cy.login(artemis.users.getAdmin());
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(3, 'hours'))
                .endDate(dayjs().subtract(2, 'hours').add(100, 'seconds'))
                .publishResultsDate(dayjs().subtract(2, 'hours').add(50, 'seconds'))
                .gracePeriod(0)
                .build();
            courseManagementRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                courseManagementRequests.registerStudentForExam(course, exam, student);
                courseManagementRequests.addExerciseGroupForExam(course, exam, 'group 1', true).then((groupResponse) => {
                    exerciseGroup = groupResponse.body;
                });
            });
        });

        afterEach('Delete Exam', () => {
            cy.login(artemis.users.getAdmin());
            courseManagementRequests.deleteExam(course, exam);
        });

        it.only('assess a programming exercise submission (MANUAL)', () => {
            cy.login(artemis.users.getAdmin());
            courseManagementRequests
                .createProgrammingExercise(programmingExerciseName, programmingExerciseShortName, packageName, null, exerciseGroup)
                .then((progRespone) => {
                    const programmingExercise = progRespone.body;
                    courseManagementRequests.generateMissingIndividualExams(course, exam);
                    courseManagementRequests.prepareExerciseStartForExam(course, exam);
                    cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                    examStartEnd.startExam();
                    cy.contains(programmingExercise.title).should('be.visible').click();
                    makeSubmissionAndVerifyResults(partiallySuccessful, () => {
                        cy.get('.btn-danger').click();
                        examStartEnd.finishExam();
                        cy.get('.alert').should('be.visible');
                        cy.login(tutor, '/course-management/' + course.id + '/exams');
                        cy.contains('Assessment Dashboard', {timeout: 40000}).click();
                        cy.get('[jhitranslate="entity.action.exerciseDashboard"]').should('be.visible').click();
                        cy.contains('Start participating in the exercise').click();
                        cy.contains('Start new assessment').click();
                        cy.get('.btn').contains('Add new Feedback').click();
                        cy.get('.col-lg-6 >>>> :nth-child(1) > :nth-child(2)').clear().type('2');
                        cy.get('.col-lg-6 >>>> :nth-child(2) > :nth-child(2)').type('Good job');
                        cy.contains('Submit').click();
                        cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                        cy.get('.question-options').contains('6.6 of 10 points').should('be.visible');
                    });
                });
        });
    });
});

function makeSubmissionAndVerifyResults(submission: ProgrammingExerciseSubmission, verifyOutput: () => void) {
    // We create an empty file so that the file browser does not create an extra subfolder when all files are deleted
    editorPage.createFileInRootPackage('placeholderFile');
    // We delete all existing files, so we can create new files and don't have to delete their already existing content
    editorPage.deleteFile('Client.java');
    editorPage.deleteFile('BubbleSort.java');
    editorPage.deleteFile('MergeSort.java');
    editorPage.typeSubmission(submission, packageName);
    editorPage.submit();
    verifyOutput();
}

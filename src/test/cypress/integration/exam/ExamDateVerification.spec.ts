import { CypressExamBuilder } from '../../support/requests/CourseManagementRequests';
import dayjs from 'dayjs';
import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// page objects
const examStartEnd = artemis.pageobjects.examStartEnd;
const textEditor = artemis.pageobjects.textExercise.editor;
const examNavigationBar = artemis.pageobjects.examNavigationBar;
const courseOverview = artemis.pageobjects.courseOverview;

describe('Exam date verification', () => {
    let course: any;
    let examTitle: string;

    before(() => {
        cy.login(artemis.users.getAdmin());
        courseManagementRequests.createCourse().then((response) => {
            course = response.body;
            courseManagementRequests.addStudentToCourse(course.id, artemis.users.getStudentOne().username);
        });
    });

    beforeEach(() => {
        examTitle = 'exam' + generateUUID();
        cy.login(artemis.users.getAdmin(), '/');
    });

    describe('Exam timing', () => {
        let exam: any;
        let textExercise: any;
        it('Does not show exam before visible date', () => {
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().add(1, 'day'))
                .startDate(dayjs().add(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .build();
            courseManagementRequests.createExam(examContent).then((response) => {
                exam = response.body;
            });
            cy.login(artemis.users.getStudentOne(), `/courses`);
            cy.contains(examTitle).should('not.exist');
            cy.visit(`/courses/${course.id}`);
            cy.url().should('contain', `${course.id}`);
            cy.contains(examTitle).should('not.exist');
        });

        it('Shows after visible date', () => {
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(5, 'days'))
                .startDate(dayjs().add(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .build();
            courseManagementRequests.createExam(examContent).then((response) => {
                exam = response.body;
                courseManagementRequests.registerStudentForExam(exam, artemis.users.getStudentOne());
                cy.login(artemis.users.getStudentOne(), `/courses/${course.id}`);
                cy.url().should('contain', `${course.id}`);
                courseOverview.openExamsTab();
                courseOverview.openExam(exam.id);
                cy.url().should('contain', `/exams/${exam.id}`);
            });
        });

        it('Student can start after start Date', () => {
            let exerciseGroup: any;
            const student = artemis.users.getStudentOne();
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .build();
            courseManagementRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                courseManagementRequests.registerStudentForExam(exam, student);
                courseManagementRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                    exerciseGroup = groupResponse.body;
                    courseManagementRequests.createTextExercise({ exerciseGroup }).then((exerciseResponse) => {
                        textExercise = exerciseResponse.body;
                        courseManagementRequests.generateMissingIndividualExams(exam);
                        courseManagementRequests.prepareExerciseStartForExam(exam);
                        cy.login(student, `/courses/${course.id}/exams`);
                        courseOverview.openExam(exam.id);
                        cy.url().should('contain', `/exams/${exam.id}`);
                        cy.contains(exam.title).should('be.visible');
                        examStartEnd.startExam();
                        examNavigationBar.openExerciseAtIndex(0);
                        cy.fixture('loremIpsum.txt').then((submission) => {
                            textEditor.typeSubmission(submission);
                        });
                        examNavigationBar.clickSave();
                    });
                });
            });
        });

        it('Exam ends after end time', () => {
            let exerciseGroup: any;
            const student = artemis.users.getStudentOne();
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(dayjs().add(30, 'seconds'))
                .build();
            courseManagementRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                courseManagementRequests.registerStudentForExam(exam, student);
                courseManagementRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                    exerciseGroup = groupResponse.body;
                    courseManagementRequests.createTextExercise({ exerciseGroup }).then((response) => {
                        textExercise = response.body;
                        courseManagementRequests.generateMissingIndividualExams(exam);
                        courseManagementRequests.prepareExerciseStartForExam(exam);
                        cy.login(student, `/courses/${course.id}/exams`);
                        courseOverview.openExam(exam.id);
                        cy.contains(exam.title).should('be.visible');
                        examStartEnd.startExam();
                        examNavigationBar.openExerciseAtIndex(0);
                        cy.fixture('loremIpsum.txt').then((submissionText) => {
                            textEditor.typeSubmission(submissionText);
                        });
                        examNavigationBar.clickSave();
                        cy.contains('This is the end of ' + exam.title, { timeout: 40000 });
                        examStartEnd.finishExam();
                    });
                });
            });
        });

        afterEach(() => {
            cy.login(artemis.users.getAdmin());
            courseManagementRequests.deleteExam(exam);
        });
    });

    after(() => {
        if (!!course) {
            cy.login(artemis.users.getAdmin());
            courseManagementRequests.deleteCourse(course.id);
        }
    });
});

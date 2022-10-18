import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { CypressExamBuilder, parseCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import dayjs from 'dayjs/esm';
import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// page objects
const courseOverview = artemis.pageobjects.course.overview;
const examNavigationBar = artemis.pageobjects.exam.navigationBar;
const examStartEnd = artemis.pageobjects.exam.startEnd;
const textEditor = artemis.pageobjects.exercise.text.editor;

describe('Exam date verification', () => {
    let course: Course;
    let examTitle: string;

    before(() => {
        cy.login(artemis.users.getAdmin());
        courseManagementRequests.createCourse().then((response) => {
            course = parseCourseAfterMultiPart(response);
            courseManagementRequests.addStudentToCourse(course, artemis.users.getStudentOne());
        });
    });

    beforeEach(() => {
        examTitle = 'exam' + generateUUID();
        cy.login(artemis.users.getAdmin(), '/');
    });

    describe('Exam timing', () => {
        let exam: Exam;
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
                courseOverview.openExam(exam.id!);
                cy.url().should('contain', `/exams/${exam.id}`);
            });
        });

        it('Student can start after start Date', () => {
            let exerciseGroup: ExerciseGroup;
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
                    courseManagementRequests.createTextExercise({ exerciseGroup }).then(() => {
                        courseManagementRequests.generateMissingIndividualExams(exam);
                        courseManagementRequests.prepareExerciseStartForExam(exam);
                        cy.login(student, `/courses/${course.id}/exams`);
                        courseOverview.openExam(exam.id!);
                        cy.url().should('contain', `/exams/${exam.id}`);
                        cy.contains(exam.title!).should('be.visible');
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
            let exerciseGroup: ExerciseGroup;
            const examEnd = dayjs().add(30, 'seconds');
            const student = artemis.users.getStudentOne();
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(examEnd)
                .build();
            courseManagementRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                courseManagementRequests.registerStudentForExam(exam, student);
                courseManagementRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                    exerciseGroup = groupResponse.body;
                    courseManagementRequests.createTextExercise({ exerciseGroup }).then(() => {
                        courseManagementRequests.generateMissingIndividualExams(exam);
                        courseManagementRequests.prepareExerciseStartForExam(exam);
                        cy.login(student, `/courses/${course.id}/exams`);
                        courseOverview.openExam(exam.id!);
                        cy.contains(exam.title!).should('be.visible');
                        examStartEnd.startExam();
                        examNavigationBar.openExerciseAtIndex(0);
                        cy.fixture('loremIpsum.txt').then((submissionText) => {
                            textEditor.typeSubmission(submissionText);
                        });
                        examNavigationBar.clickSave();
                        if (examEnd.isAfter(dayjs())) {
                            cy.wait(examEnd.diff(dayjs()));
                        }
                        cy.get('#exam-finished-title').should('contain.text', exam.title, { timeout: 40000 });
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
            courseManagementRequests.deleteCourse(course.id!);
        }
    });
});

import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExamBuilder, convertModelAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import dayjs from 'dayjs/esm';
import { generateUUID } from '../../support/utils';
import { courseManagementRequest, courseOverview, examNavigation, examParticipation, examStartEnd, textExerciseEditor } from '../../support/artemis';
import { admin, studentOne } from '../../support/users';

describe('Exam date verification', () => {
    let course: Course;
    let examTitle: string;

    before('Create course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementRequest.addStudentToCourse(course, studentOne);
        });
    });

    beforeEach(() => {
        examTitle = 'exam' + generateUUID();
        cy.login(admin, '/');
    });

    describe('Exam timing', () => {
        let exam: Exam;
        it('Does not show exam before visible date', () => {
            const examContent = new ExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().add(1, 'day'))
                .startDate(dayjs().add(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .build();
            courseManagementRequest.createExam(examContent).then((response) => {
                exam = response.body;
            });
            cy.login(studentOne, `/courses`);
            cy.contains(examTitle).should('not.exist');
            cy.visit(`/courses/${course.id}`);
            cy.url().should('contain', `${course.id}`);
            cy.contains(examTitle).should('not.exist');
        });

        it('Shows after visible date', () => {
            const examContent = new ExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(5, 'days'))
                .startDate(dayjs().add(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .build();
            courseManagementRequest.createExam(examContent).then((response) => {
                exam = response.body;
                courseManagementRequest.registerStudentForExam(exam, studentOne);
                cy.login(studentOne, `/courses/${course.id}`);
                cy.url().should('contain', `${course.id}`);
                courseOverview.openExamsTab();
                courseOverview.openExam(exam.id!);
                cy.url().should('contain', `/exams/${exam.id}`);
            });
        });

        it('Student can start after start Date', () => {
            let exerciseGroup: ExerciseGroup;
            const examContent = new ExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .build();
            courseManagementRequest.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                courseManagementRequest.registerStudentForExam(exam, studentOne);
                courseManagementRequest.addExerciseGroupForExam(exam).then((groupResponse) => {
                    exerciseGroup = groupResponse.body;
                    courseManagementRequest.createTextExercise({ exerciseGroup }).then((exerciseResponse) => {
                        const exercise = exerciseResponse.body;
                        courseManagementRequest.generateMissingIndividualExams(exam);
                        courseManagementRequest.prepareExerciseStartForExam(exam);
                        cy.login(studentOne, `/courses/${course.id}/exams`);
                        courseOverview.openExam(exam.id!);
                        cy.url().should('contain', `/exams/${exam.id}`);
                        cy.contains(exam.title!).should('be.visible');
                        examStartEnd.startExam();
                        examNavigation.openExerciseAtIndex(0);
                        cy.fixture('loremIpsum-short.txt').then((submission) => {
                            textExerciseEditor.typeSubmission(exercise.id, submission);
                        });
                        examNavigation.clickSave();
                    });
                });
            });
        });

        it('Exam ends after end time', () => {
            let exerciseGroup: ExerciseGroup;
            const examEnd = dayjs().add(30, 'seconds');
            const examContent = new ExamBuilder(course).title(examTitle).visibleDate(dayjs().subtract(3, 'days')).startDate(dayjs().subtract(2, 'days')).endDate(examEnd).build();
            courseManagementRequest.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                courseManagementRequest.registerStudentForExam(exam, studentOne);
                courseManagementRequest.addExerciseGroupForExam(exam).then((groupResponse) => {
                    exerciseGroup = groupResponse.body;
                    courseManagementRequest.createTextExercise({ exerciseGroup }).then((exerciseResponse) => {
                        const exercise = exerciseResponse.body;
                        courseManagementRequest.generateMissingIndividualExams(exam);
                        courseManagementRequest.prepareExerciseStartForExam(exam);
                        cy.login(studentOne, `/courses/${course.id}/exams`);
                        courseOverview.openExam(exam.id!);
                        cy.contains(exam.title!).should('be.visible');
                        examStartEnd.startExam();
                        examNavigation.openExerciseAtIndex(0);
                        cy.fixture('loremIpsum-short.txt').then((submissionText) => {
                            textExerciseEditor.typeSubmission(exercise.id, submissionText);
                        });
                        examNavigation.clickSave();
                        if (examEnd.isAfter(dayjs())) {
                            cy.wait(examEnd.diff(dayjs()));
                        }
                        examParticipation.checkExamFinishedTitle(exam.title!);
                        examStartEnd.finishExam();
                    });
                });
            });
        });
    });

    after('Delete course', () => {
        courseManagementRequest.deleteCourse(course, admin);
    });
});

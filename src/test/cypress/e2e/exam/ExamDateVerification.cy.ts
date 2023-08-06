import dayjs from 'dayjs/esm';

import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

import {
    courseManagementAPIRequest,
    courseOverview,
    examAPIRequests,
    examNavigation,
    examParticipation,
    examStartEnd,
    exerciseAPIRequest,
    textExerciseEditor,
} from '../../support/artemis';
import { admin, studentOne } from '../../support/users';
import { convertModelAfterMultiPart, generateUUID } from '../../support/utils';

describe('Exam date verification', () => {
    let course: Course;
    let examTitle: string;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
        });
    });

    beforeEach(() => {
        examTitle = 'exam' + generateUUID();
        cy.login(admin, '/');
    });

    describe('Exam timing', () => {
        let exam: Exam;
        it('Does not show exam before visible date', () => {
            const examConfig: Exam = {
                course,
                title: examTitle,
                visibleDate: dayjs().add(1, 'day'),
                startDate: dayjs().add(2, 'days'),
                endDate: dayjs().add(3, 'days'),
            };
            examAPIRequests.createExam(examConfig).then((response) => {
                exam = response.body;
            });
            cy.login(studentOne, `/courses`);
            cy.contains(examTitle).should('not.exist');
            cy.visit(`/courses/${course.id}`);
            cy.url().should('contain', `${course.id}`);
            cy.contains(examTitle).should('not.exist');
        });

        it('Shows after visible date', () => {
            const examConfig: Exam = {
                course,
                title: examTitle,
                visibleDate: dayjs().subtract(5, 'days'),
                startDate: dayjs().add(2, 'days'),
                endDate: dayjs().add(3, 'days'),
            };
            examAPIRequests.createExam(examConfig).then((response) => {
                exam = response.body;
                examAPIRequests.registerStudentForExam(exam, studentOne);
                cy.login(studentOne, `/courses/${course.id}`);
                cy.url().should('contain', `${course.id}`);
                courseOverview.openExamsTab();
                courseOverview.openExam(exam.id!);
                cy.url().should('contain', `/exams/${exam.id}`);
            });
        });

        it('Student can start after start Date', () => {
            let exerciseGroup: ExerciseGroup;
            const examConfig: Exam = {
                course,
                title: examTitle,
                visibleDate: dayjs().subtract(3, 'days'),
                startDate: dayjs().subtract(2, 'days'),
                endDate: dayjs().add(3, 'days'),
            };
            examAPIRequests.createExam(examConfig).then((examResponse) => {
                exam = examResponse.body;
                examAPIRequests.registerStudentForExam(exam, studentOne);
                examAPIRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                    exerciseGroup = groupResponse.body;
                    exerciseAPIRequest.createTextExercise({ exerciseGroup }).then((exerciseResponse) => {
                        const exercise = exerciseResponse.body;
                        examAPIRequests.generateMissingIndividualExams(exam);
                        examAPIRequests.prepareExerciseStartForExam(exam);
                        cy.login(studentOne, `/courses/${course.id}/exams`);
                        courseOverview.openExam(exam.id!);
                        cy.url().should('contain', `/exams/${exam.id}`);
                        cy.contains(exam.title!).should('be.visible');
                        examStartEnd.startExam();
                        examNavigation.openExerciseAtIndex(0);
                        cy.fixture('loremIpsum-short.txt').then((submission) => {
                            textExerciseEditor.typeSubmission(exercise.id!, submission);
                        });
                        examNavigation.clickSave();
                    });
                });
            });
        });

        it('Exam ends after end time', () => {
            let exerciseGroup: ExerciseGroup;
            const examEnd = dayjs().add(30, 'seconds');
            const examConfig: Exam = {
                course,
                title: examTitle,
                visibleDate: dayjs().subtract(3, 'days'),
                startDate: dayjs().subtract(2, 'days'),
                endDate: examEnd,
            };
            examAPIRequests.createExam(examConfig).then((examResponse) => {
                exam = examResponse.body;
                examAPIRequests.registerStudentForExam(exam, studentOne);
                examAPIRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                    exerciseGroup = groupResponse.body;
                    exerciseAPIRequest.createTextExercise({ exerciseGroup }).then((exerciseResponse) => {
                        const exercise = exerciseResponse.body;
                        examAPIRequests.generateMissingIndividualExams(exam);
                        examAPIRequests.prepareExerciseStartForExam(exam);
                        cy.login(studentOne, `/courses/${course.id}/exams`);
                        courseOverview.openExam(exam.id!);
                        cy.contains(exam.title!).should('be.visible');
                        examStartEnd.startExam();
                        examNavigation.openExerciseAtIndex(0);
                        cy.fixture('loremIpsum-short.txt').then((submissionText) => {
                            textExerciseEditor.typeSubmission(exercise.id!, submissionText);
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
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});

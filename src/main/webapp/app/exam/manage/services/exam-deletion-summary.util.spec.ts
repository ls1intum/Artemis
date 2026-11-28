import { describe, expect, it } from 'vitest';
import { buildExamDeletionSummary, buildExerciseGroupDeletionSummary } from 'app/exam/manage/services/exam-deletion-summary.util';

describe('exam deletion summary util', () => {
    const prefix = 'artemisApp.examManagement.delete.summary';

    describe('buildExamDeletionSummary', () => {
        it('groups everything that is deleted with an exam', () => {
            const categories = buildExamDeletionSummary({
                numberOfExerciseGroups: 2,
                numberOfProgrammingExercises: 1,
                numberOfQuizExercises: 1,
                numberRegisteredStudents: 150,
                numberNotStartedExams: 10,
                numberStartedExams: 20,
                numberSubmittedExams: 120,
                numberOfTestRuns: 2,
                numberOfStudentParticipations: 600,
                numberOfSubmissions: 620,
                numberOfResults: 600,
                numberOfAssessments: 40,
                numberOfComplaints: 3,
                numberOfPlagiarismComparisons: 5,
                numberOfCommunicationPosts: 7,
                numberOfAnswerPosts: 4,
                numberOfBuilds: 900,
            });

            expect(categories.map((category) => category.titleKey)).toEqual([
                `${prefix}.category.structure`,
                `${prefix}.category.students`,
                `${prefix}.category.studentWork`,
                `${prefix}.category.assessment`,
                `${prefix}.category.plagiarism`,
                `${prefix}.category.communication`,
                `${prefix}.category.infrastructure`,
            ]);
            const students = categories[1].items;
            expect(students.map((item) => item.labelKey)).toEqual([
                `${prefix}.numberRegisteredStudents`,
                `${prefix}.numberNotStartedExams`,
                `${prefix}.numberStartedExams`,
                `${prefix}.numberSubmittedExams`,
                `${prefix}.numberTestRuns`,
            ]);
            // registered students and builds are no student work that would be lost, so they are never emphasised, however large they are
            expect(students[0]).toEqual({ labelKey: `${prefix}.numberRegisteredStudents`, value: 150, highlight: false, neverHighlight: true });
            expect(students[1].highlight).toBe(false);
            expect(categories[6].items[0]).toEqual({ labelKey: `${prefix}.numberBuilds`, value: 900, highlight: false, neverHighlight: true });
            // large amounts of student work are emphasised
            expect(categories[2].items[0]).toEqual({ labelKey: `${prefix}.numberStudentParticipations`, value: 600, highlight: true });
        });

        it('lists the exam sessions and the live events, each with its own count', () => {
            const categories = buildExamDeletionSummary({ numberOfExamSessions: 77, numberOfExamLiveEvents: 9 });

            expect(categories.map((category) => category.titleKey)).toEqual([`${prefix}.category.students`]);
            expect(categories[0].items.map(({ labelKey, value }) => ({ labelKey, value }))).toEqual([
                { labelKey: `${prefix}.numberExamSessions`, value: 77 },
                { labelKey: `${prefix}.numberExamLiveEvents`, value: 9 },
            ]);
        });

        it('lists nothing for an exam without data', () => {
            expect(buildExamDeletionSummary({})).toEqual([]);
        });

        it('leaves out categories without data', () => {
            const categories = buildExamDeletionSummary({ numberOfExerciseGroups: 1 });

            expect(categories.map((category) => category.titleKey)).toEqual([`${prefix}.category.structure`]);
        });
    });

    describe('buildExerciseGroupDeletionSummary', () => {
        it('groups everything that is deleted with an exercise group and shares labels with the exam', () => {
            const categories = buildExerciseGroupDeletionSummary({
                numberOfProgrammingExercises: 1,
                numberOfTextExercises: 2,
                numberOfStudentExams: 30,
                numberOfStudentParticipations: 60,
                numberOfResults: 60,
                numberOfBuilds: 10,
            });

            expect(categories.map((category) => category.titleKey)).toEqual([
                `${prefix}.category.structure`,
                `${prefix}.category.studentWork`,
                `${prefix}.category.assessment`,
                `${prefix}.category.infrastructure`,
            ]);
            expect(categories[1].items.map((item) => item.labelKey)).toEqual([`${prefix}.numberStudentExamsOfGroup`, `${prefix}.numberStudentParticipations`]);
        });

        it('lists nothing for an empty exercise group', () => {
            expect(buildExerciseGroupDeletionSummary({})).toEqual([]);
        });
    });
});

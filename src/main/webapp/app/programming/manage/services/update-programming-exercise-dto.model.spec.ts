import dayjs from 'dayjs/esm';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { toUpdateProgrammingExerciseDTO } from './update-programming-exercise-dto.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { DifficultyLevel, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';

describe('UpdateProgrammingExerciseDTO', () => {
    describe('toUpdateProgrammingExerciseDTO', () => {
        it('should convert a course programming exercise to an update DTO', () => {
            const course = { id: 1 } as Course;
            const exercise = new ProgrammingExercise(course, undefined);
            exercise.id = 10;
            exercise.title = 'Test Exercise';
            exercise.shortName = 'test';
            exercise.programmingLanguage = ProgrammingLanguage.JAVA;
            exercise.maxPoints = 100;
            exercise.bonusPoints = 0;
            exercise.difficulty = DifficultyLevel.MEDIUM;
            exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
            exercise.releaseDate = dayjs('2026-01-01T10:00:00Z');
            exercise.dueDate = dayjs('2026-01-15T23:59:00Z');
            exercise.allowOnlineEditor = true;
            exercise.allowOfflineIde = true;
            exercise.allowOnlineIde = false;
            exercise.showTestNamesToStudents = true;
            exercise.releaseTestsWithExampleSolution = false;

            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.id).toBe(10);
            expect(dto.title).toBe('Test Exercise');
            expect(dto.shortName).toBe('test');
            expect(dto.programmingLanguage).toBe(ProgrammingLanguage.JAVA);
            expect(dto.maxPoints).toBe(100);
            expect(dto.difficulty).toBe(DifficultyLevel.MEDIUM);
            expect(dto.courseId).toBe(1);
            expect(dto.exerciseGroupId).toBeUndefined();
            expect(dto.allowOnlineEditor).toBeTrue();
            expect(dto.allowOnlineIde).toBeFalse();
            expect(dto.showTestNamesToStudents).toBeTrue();
            expect(dto.releaseTestsWithExampleSolution).toBeFalse();
            expect(dto.releaseDate).toBe(exercise.releaseDate!.toJSON());
            expect(dto.dueDate).toBe(exercise.dueDate!.toJSON());
        });

        it('should set exerciseGroupId for exam exercises', () => {
            const exerciseGroup = { id: 5 } as ExerciseGroup;
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.id = 11;
            exercise.title = 'Exam Exercise';
            exercise.maxPoints = 50;
            exercise.exerciseGroup = exerciseGroup;
            exercise.allowOnlineIde = false;
            exercise.showTestNamesToStudents = false;
            exercise.releaseTestsWithExampleSolution = false;

            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.exerciseGroupId).toBe(5);
            expect(dto.courseId).toBeUndefined();
        });

        it('should convert build config when present', () => {
            const exercise = new ProgrammingExercise({ id: 1 } as Course, undefined);
            exercise.id = 12;
            exercise.title = 'Build Config Exercise';
            exercise.maxPoints = 100;
            exercise.allowOnlineIde = false;
            exercise.showTestNamesToStudents = false;
            exercise.releaseTestsWithExampleSolution = false;
            exercise.buildConfig = {
                sequentialTestRuns: true,
                checkoutSolutionRepository: true,
                timeoutSeconds: 300,
                allowBranching: false,
                branchRegex: '.*',
                buildScript: '#!/bin/bash\necho test',
            };

            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.buildConfig).toBeDefined();
            expect(dto.buildConfig!.sequentialTestRuns).toBeTrue();
            expect(dto.buildConfig!.checkoutSolutionRepository).toBeTrue();
            expect(dto.buildConfig!.timeoutSeconds).toBe(300);
            expect(dto.buildConfig!.allowBranching).toBeFalse();
            expect(dto.buildConfig!.buildScript).toBe('#!/bin/bash\necho test');
        });

        it('should handle grading criteria conversion', () => {
            const exercise = new ProgrammingExercise({ id: 1 } as Course, undefined);
            exercise.id = 13;
            exercise.title = 'Grading Exercise';
            exercise.maxPoints = 100;
            exercise.allowOnlineIde = false;
            exercise.showTestNamesToStudents = false;
            exercise.releaseTestsWithExampleSolution = false;
            exercise.gradingCriteria = [
                {
                    id: 1,
                    title: 'Criterion 1',
                    structuredGradingInstructions: [
                        {
                            id: 10,
                            credits: 5,
                            gradingScale: 'Good',
                            instructionDescription: 'Well done',
                            feedback: 'Great work',
                            usageCount: 1,
                        },
                    ],
                },
            ];

            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.gradingCriteria).toHaveLength(1);
            expect(dto.gradingCriteria![0].title).toBe('Criterion 1');
            expect(dto.gradingCriteria![0].structuredGradingInstructions).toHaveLength(1);
            expect(dto.gradingCriteria![0].structuredGradingInstructions![0].credits).toBe(5);
        });
    });
});

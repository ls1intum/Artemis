import { describe, expect, it } from 'vitest';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Exercise, ExerciseMode, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TeamAssignmentConfig } from 'app/exercise/shared/entities/team/team-assignment-config.model';
import { ExerciseValidationViewState, getCommonExerciseInvalidReasons, getPlagiarismInvalidReasons } from 'app/exercise/util/exercise-validation.util';

describe('ExerciseValidationUtil', () => {
    const validViewState = (): ExerciseValidationViewState => ({
        isExamMode: false,
        minTitleLength: 3,
        isTitleDisallowed: false,
        isChannelNameRequired: true,
        timelineStatus: { valid: true, empty: false, invalidItems: [] },
        isExampleSolutionPublicationDateInputValid: true,
    });

    const validExercise = (): Exercise => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.title = 'Valid title';
        exercise.channelName = 'valid-title';
        exercise.mode = ExerciseMode.INDIVIDUAL;
        exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
        exercise.maxPoints = 10;
        exercise.bonusPoints = 0;
        return exercise;
    };

    const translateKeys = (exercise: Exercise, viewState: ExerciseValidationViewState) => getCommonExerciseInvalidReasons(exercise, viewState).map((reason) => reason.translateKey);

    it('should report no reason for a fully valid exercise', () => {
        expect(getCommonExerciseInvalidReasons(validExercise(), validViewState())).toEqual([]);
    });

    describe('title', () => {
        it('should report a missing title', () => {
            const exercise = validExercise();
            exercise.title = undefined;

            expect(translateKeys(exercise, validViewState())).toEqual(['artemisApp.exercise.form.title.undefined']);
        });

        it('should report an empty title', () => {
            const exercise = validExercise();
            exercise.title = '';

            expect(translateKeys(exercise, validViewState())).toEqual(['artemisApp.exercise.form.title.undefined']);
        });

        it('should report a title shorter than the minimum', () => {
            const exercise = validExercise();
            exercise.title = 'ab';

            expect(getCommonExerciseInvalidReasons(exercise, validViewState())).toEqual([
                { translateKey: 'artemisApp.exercise.form.title.minlength', translateValues: { min: 3 } },
            ]);
        });

        it('should not enforce a minimum length when none is configured', () => {
            const exercise = validExercise();
            exercise.title = 'ab';

            expect(translateKeys(exercise, { ...validViewState(), minTitleLength: undefined })).toEqual([]);
        });

        it('should report a title already used by another exercise', () => {
            expect(translateKeys(validExercise(), { ...validViewState(), isTitleDisallowed: true })).toEqual(['artemisApp.exercise.form.title.disallowedValue']);
        });
    });

    describe('channel name', () => {
        it('should report a missing channel name when one is required', () => {
            const exercise = validExercise();
            exercise.channelName = '';

            expect(translateKeys(exercise, validViewState())).toEqual(['artemisApp.exercise.form.channelName.empty']);
        });

        it('should ignore a missing channel name when none is required', () => {
            const exercise = validExercise();
            exercise.channelName = undefined;

            expect(translateKeys(exercise, { ...validViewState(), isChannelNameRequired: false })).toEqual([]);
        });
    });

    describe('points', () => {
        it('should report missing points', () => {
            const exercise = validExercise();
            exercise.maxPoints = undefined;

            expect(translateKeys(exercise, validViewState())).toEqual(['artemisApp.exercise.form.points.undefined']);
        });

        it('should report a null points value as missing', () => {
            const exercise = validExercise();
            exercise.maxPoints = null as unknown as number;

            expect(translateKeys(exercise, validViewState())).toEqual(['artemisApp.exercise.form.points.undefined']);
        });

        it('should report points below the minimum', () => {
            const exercise = validExercise();
            exercise.maxPoints = 0;

            expect(translateKeys(exercise, validViewState())).toEqual(['artemisApp.exercise.form.points.customMin']);
        });

        it('should report points above the maximum', () => {
            const exercise = validExercise();
            exercise.maxPoints = 10000;

            expect(translateKeys(exercise, validViewState())).toEqual(['artemisApp.exercise.form.points.customMax']);
        });

        it('should accept points at the maximum boundary', () => {
            const exercise = validExercise();
            exercise.maxPoints = 9999;

            expect(translateKeys(exercise, validViewState())).toEqual([]);
        });
    });

    describe('bonus points', () => {
        it('should report missing bonus points when the exercise is included completely', () => {
            const exercise = validExercise();
            exercise.bonusPoints = undefined;

            expect(translateKeys(exercise, validViewState())).toEqual(['artemisApp.exercise.form.bonusPoints.undefined']);
        });

        it('should report a null bonus points value as missing', () => {
            const exercise = validExercise();
            exercise.bonusPoints = null as unknown as number;

            expect(translateKeys(exercise, validViewState())).toEqual(['artemisApp.exercise.form.bonusPoints.undefined']);
        });

        it('should not require bonus points when the exercise is not included completely', () => {
            const exercise = validExercise();
            exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_AS_BONUS;
            exercise.bonusPoints = undefined;

            expect(translateKeys(exercise, validViewState())).toEqual([]);
        });

        it('should report bonus points outside the allowed range', () => {
            const exercise = validExercise();
            exercise.bonusPoints = -1;

            expect(translateKeys(exercise, validViewState())).toEqual(['artemisApp.exercise.form.bonusPoints.customMin']);

            exercise.bonusPoints = 10000;

            expect(translateKeys(exercise, validViewState())).toEqual(['artemisApp.exercise.form.bonusPoints.customMax']);
        });

        it('should enforce bonus points bounds even when the exercise is not included completely', () => {
            const exercise = validExercise();
            exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_AS_BONUS;
            exercise.bonusPoints = 10000;

            expect(translateKeys(exercise, validViewState())).toEqual(['artemisApp.exercise.form.bonusPoints.customMax']);
        });
    });

    describe('team size', () => {
        const teamExercise = (minTeamSize?: number, maxTeamSize?: number): Exercise => {
            const exercise = validExercise();
            exercise.mode = ExerciseMode.TEAM;
            exercise.teamAssignmentConfig = new TeamAssignmentConfig();
            exercise.teamAssignmentConfig.minTeamSize = minTeamSize;
            exercise.teamAssignmentConfig.maxTeamSize = maxTeamSize;
            return exercise;
        };

        it('should ignore team size for individual exercises', () => {
            expect(translateKeys(validExercise(), validViewState())).toEqual([]);
        });

        it('should report missing team sizes', () => {
            expect(translateKeys(teamExercise(undefined, undefined), validViewState())).toEqual([
                'artemisApp.exercise.form.minTeamSize.required',
                'artemisApp.exercise.form.maxTeamSize.required',
            ]);
        });

        it('should report team sizes outside the allowed range', () => {
            expect(translateKeys(teamExercise(0, 100), validViewState())).toEqual(['artemisApp.exercise.form.minTeamSize.min', 'artemisApp.exercise.form.maxTeamSize.max']);
        });

        it('should accept valid team sizes', () => {
            expect(translateKeys(teamExercise(1, 5), validViewState())).toEqual([]);
        });

        it('should accept a team size at the maximum boundary', () => {
            expect(translateKeys(teamExercise(1, 99), validViewState())).toEqual([]);
        });
    });

    describe('timeline and example solution publication date', () => {
        it('should forward the timeline reasons with the offending date name', () => {
            const viewState: ExerciseValidationViewState = {
                ...validViewState(),
                timelineStatus: {
                    valid: false,
                    empty: true,
                    invalidItems: [{ labelStringKey: 'artemisApp.exercise.dueDate', reasonKey: 'artemisApp.exercise.form.timeline.order', dateName: 'Due Date' }],
                },
            };

            expect(getCommonExerciseInvalidReasons(validExercise(), viewState)).toEqual([
                { translateKey: 'artemisApp.exercise.form.timeline.order', translateValues: { dateName: 'Due Date' } },
            ]);
        });

        it('should report an ordering error on the example solution publication date', () => {
            const exercise = validExercise();
            exercise.exampleSolutionPublicationDateError = true;

            expect(translateKeys(exercise, validViewState())).toEqual(['artemisApp.exercise.exampleSolutionPublicationDateError']);
        });

        it('should report a malformed example solution publication date', () => {
            expect(translateKeys(validExercise(), { ...validViewState(), isExampleSolutionPublicationDateInputValid: false })).toEqual([
                'artemisApp.exercise.form.exampleSolutionPublicationDate.invalidInput',
            ]);
        });

        it('should still report an invalid title in exam mode', () => {
            const exercise = validExercise();
            exercise.title = undefined;

            expect(translateKeys(exercise, { ...validViewState(), isExamMode: true })).toEqual(['artemisApp.exercise.form.title.undefined']);
        });

        it('should skip timeline and solution date checks in exam mode', () => {
            const exercise = validExercise();
            exercise.exampleSolutionPublicationDateError = true;
            const viewState: ExerciseValidationViewState = {
                ...validViewState(),
                isExamMode: true,
                isExampleSolutionPublicationDateInputValid: false,
                timelineStatus: {
                    valid: false,
                    empty: true,
                    invalidItems: [{ labelStringKey: 'artemisApp.exercise.dueDate', reasonKey: 'artemisApp.exercise.form.timeline.order', dateName: 'Due Date' }],
                },
            };

            expect(translateKeys(exercise, viewState)).toEqual([]);
        });
    });

    describe('getPlagiarismInvalidReasons', () => {
        const plagiarismComponent = (isFormValid: boolean, invalidControlNames: string[] = []) =>
            ({
                isFormValid: () => isFormValid,
                form: {
                    controls: {
                        similarityThreshold: { invalid: invalidControlNames.includes('similarityThreshold') },
                        minimumScore: { invalid: invalidControlNames.includes('minimumScore') },
                        minimumSize: { invalid: invalidControlNames.includes('minimumSize') },
                        continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: {
                            invalid: invalidControlNames.includes('continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod'),
                        },
                    },
                },
            }) as never;

        it('should report nothing when no component is rendered', () => {
            expect(getPlagiarismInvalidReasons(undefined)).toEqual([]);
        });

        it('should report nothing when the plagiarism form is valid', () => {
            expect(getPlagiarismInvalidReasons(plagiarismComponent(true, ['minimumScore']))).toEqual([]);
        });

        it('should report one reason per invalid plagiarism control', () => {
            expect(getPlagiarismInvalidReasons(plagiarismComponent(false, ['similarityThreshold', 'minimumSize']))).toEqual([
                { translateKey: 'artemisApp.exercise.form.continuousPlagiarismControl.similarityThreshold.pattern', translateValues: {} },
                { translateKey: 'artemisApp.exercise.form.continuousPlagiarismControl.minimumSize.customMin', translateValues: {} },
            ]);
        });
    });
});

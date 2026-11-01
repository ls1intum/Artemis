import { DestroyRef, Signal, computed, effect, inject, signal, untracked } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { BuildTimeWindow } from 'app/programming/shared/entities/programming-exercise-build-advanced-settings';

/** The longest delay a browser timer accepts (2^31 - 1 ms, about 24.8 days). Longer waits are split into several timers. */
const MAX_TIMER_DELAY_MS = 2147483647;

/** Where "now" lies relative to the build time window of an exercise. */
export enum BuildWindowPhase {
    /** No window is set, every push is built. */
    NONE = 'none',
    BEFORE = 'before',
    DURING = 'during',
    AFTER = 'after',
}

/** Why the window of an exercise is invalid. Mirrors the server-side validation, which stays authoritative. */
export enum BuildTimeWindowError {
    END_NOT_AFTER_START = 'endNotAfterStart',
    OUTSIDE_EXERCISE_DATES = 'outsideExerciseDates',
}

/** The translation keys of the problems, used in the form and in the reasons shown next to the save button. */
export const BUILD_TIME_WINDOW_ERROR_TRANSLATION_KEYS: Record<BuildTimeWindowError, string> = {
    [BuildTimeWindowError.END_NOT_AFTER_START]: 'artemisApp.programmingExercise.buildTimeWindow.endNotAfterStart',
    [BuildTimeWindowError.OUTSIDE_EXERCISE_DATES]: 'artemisApp.programmingExercise.buildTimeWindow.outsideExerciseDates',
};

/**
 * Converts a value that is either a dayjs object or a date string (the server sends strings) into a valid dayjs object.
 *
 * @param value the value to convert
 * @return the valid dayjs object, or undefined when there is none
 */
const toValidDayjs = (value: dayjs.Dayjs | string | undefined | null): dayjs.Dayjs | undefined => {
    if (!value) {
        return undefined;
    }
    const parsed = dayjs(value);
    return parsed.isValid() ? parsed : undefined;
};

/**
 * Normalizes a window to valid dayjs bounds, so it works for exercises whose dates were not converted yet.
 *
 * @param buildTimeWindow the window as stored on the build config
 * @return the window, or undefined when it has no valid bound (which means that there is no window)
 */
export const normalizeBuildTimeWindow = (buildTimeWindow: BuildTimeWindow | undefined): BuildTimeWindow | undefined => {
    const start = toValidDayjs(buildTimeWindow?.start);
    const end = toValidDayjs(buildTimeWindow?.end);
    return start || end ? { start, end } : undefined;
};

/**
 * Reads the build time window of a programming exercise.
 *
 * @param exercise any exercise, only programming exercises can have a window
 * @return the window, or undefined when the exercise has none
 */
export const getBuildTimeWindow = (exercise: Exercise | undefined): BuildTimeWindow | undefined => {
    if (exercise?.type !== ExerciseType.PROGRAMMING) {
        return undefined;
    }
    return normalizeBuildTimeWindow((exercise as ProgrammingExercise).buildConfig?.advancedSettings?.buildTimeWindow);
};

/**
 * Reads the build time window that applies to a participation. Like the due date, the end of the window moves later by the
 * individual due date extension of the participation (the time between the exercise due date and the individual due date).
 *
 * @param exercise any exercise, only programming exercises can have a window
 * @param participation the participation of the student, undefined when the window of the exercise is wanted
 * @return the window, or undefined when the exercise has none
 */
export const getBuildTimeWindowFor = (exercise: Exercise | undefined, participation: Participation | undefined): BuildTimeWindow | undefined => {
    const buildTimeWindow = getBuildTimeWindow(exercise);
    const dueDate = toValidDayjs(exercise?.dueDate);
    const individualDueDate = toValidDayjs(participation?.individualDueDate);
    if (!buildTimeWindow?.end || !dueDate || !individualDueDate || !individualDueDate.isAfter(dueDate)) {
        return buildTimeWindow;
    }
    return { start: buildTimeWindow.start, end: buildTimeWindow.end.add(individualDueDate.diff(dueDate), 'millisecond') };
};

/**
 * Converts the dates of the advanced settings of a build config, which the server sends as strings, to dayjs objects in place.
 *
 * @param buildConfig the build config of a response
 */
export const convertBuildConfigDatesFromServer = (buildConfig: ProgrammingExerciseBuildConfig | undefined): void => {
    const buildTimeWindow = buildConfig?.advancedSettings?.buildTimeWindow;
    if (buildTimeWindow) {
        buildTimeWindow.start = toValidDayjs(buildTimeWindow.start);
        buildTimeWindow.end = toValidDayjs(buildTimeWindow.end);
    }
};

/**
 * Determines where the given point in time lies relative to the window. Both bounds are inclusive.
 *
 * @param buildTimeWindow the window, undefined when there is none
 * @param now the point in time to check
 */
export const getBuildWindowPhase = (buildTimeWindow: BuildTimeWindow | undefined, now: dayjs.Dayjs): BuildWindowPhase => {
    if (!buildTimeWindow) {
        return BuildWindowPhase.NONE;
    }
    if (buildTimeWindow.start && now.isBefore(buildTimeWindow.start)) {
        return BuildWindowPhase.BEFORE;
    }
    if (buildTimeWindow.end && now.isAfter(buildTimeWindow.end)) {
        return BuildWindowPhase.AFTER;
    }
    return BuildWindowPhase.DURING;
};

/**
 * Whether the build window applies to the pushes of the given participation. Like on the server, it applies only to
 * the student repository in graded mode and not to people who may push after the due date (editors and instructors).
 *
 * @param exercise the exercise the participation belongs to
 * @param participation the participation that is pushed to
 */
export const isBuildWindowEnforcedFor = (exercise: Exercise | undefined, participation: Participation | undefined): boolean => {
    if (!exercise || exercise.isAtLeastEditor) {
        return false;
    }
    return participation?.type === ParticipationType.PROGRAMMING && !isPracticeMode(participation);
};

/**
 * Whether a push to the participation at the given time is saved but not built because of the build window.
 *
 * @param exercise the exercise the participation belongs to
 * @param participation the participation that is pushed to
 * @param now the time of the push
 */
export const isPushOutsideBuildWindow = (exercise: Exercise | undefined, participation: Participation | undefined, now: dayjs.Dayjs = dayjs()): boolean => {
    if (!isBuildWindowEnforcedFor(exercise, participation)) {
        return false;
    }
    const phase = getBuildWindowPhase(getBuildTimeWindowFor(exercise, participation), now);
    return phase === BuildWindowPhase.BEFORE || phase === BuildWindowPhase.AFTER;
};

/**
 * Checks the window of an exercise the way the server does: the start has to be before the end,
 * and both bounds have to lie inside [start date (or release date), due date] where those dates exist.
 *
 * @param exercise the programming exercise that is edited
 * @return the first problem, or undefined when the window is valid or not set
 */
export const getBuildTimeWindowError = (exercise: ProgrammingExercise): BuildTimeWindowError | undefined => {
    const buildTimeWindow = getBuildTimeWindow(exercise);
    if (!buildTimeWindow) {
        return undefined;
    }
    const { start, end } = buildTimeWindow;
    if (start && end && !end.isAfter(start)) {
        return BuildTimeWindowError.END_NOT_AFTER_START;
    }
    const earliest = toValidDayjs(exercise.startDate) ?? toValidDayjs(exercise.releaseDate);
    const latest = toValidDayjs(exercise.dueDate);
    const bounds = [start, end].filter((bound): bound is dayjs.Dayjs => !!bound);
    if (bounds.some((bound) => (earliest && bound.isBefore(earliest)) || (latest && bound.isAfter(latest)))) {
        return BuildTimeWindowError.OUTSIDE_EXERCISE_DATES;
    }
    return undefined;
};

/**
 * Tracks where "now" lies relative to the window and updates the result when the window starts or ends.
 * Must be called in an injection context.
 *
 * @param buildTimeWindow the window to track
 */
export const injectBuildWindowPhase = (buildTimeWindow: Signal<BuildTimeWindow | undefined>): Signal<BuildWindowPhase> => {
    const destroyRef = inject(DestroyRef);
    const now = signal(dayjs());
    let timer: ReturnType<typeof setTimeout> | undefined;

    const scheduleNextChange = (trackedWindow: BuildTimeWindow | undefined) => {
        clearTimeout(timer);
        const current = dayjs();
        let nextChange: dayjs.Dayjs | undefined;
        if (trackedWindow?.start && current.isBefore(trackedWindow.start)) {
            nextChange = trackedWindow.start;
        } else if (trackedWindow?.end && !current.isAfter(trackedWindow.end)) {
            // The end is inclusive, so the phase changes one millisecond later.
            nextChange = trackedWindow.end.add(1, 'millisecond');
        }
        if (!nextChange) {
            return;
        }
        timer = setTimeout(
            () => {
                now.set(dayjs());
                scheduleNextChange(trackedWindow);
            },
            Math.min(Math.max(nextChange.diff(current), 0), MAX_TIMER_DELAY_MS),
        );
    };

    effect(() => {
        const trackedWindow = buildTimeWindow();
        untracked(() => {
            now.set(dayjs());
            scheduleNextChange(trackedWindow);
        });
    });
    destroyRef.onDestroy(() => clearTimeout(timer));

    return computed(() => getBuildWindowPhase(buildTimeWindow(), now()));
};

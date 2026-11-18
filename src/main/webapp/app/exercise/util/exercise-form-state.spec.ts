import { describe, expect, it } from 'vitest';
import { Injector, computed, effect, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ExerciseFormState } from 'app/exercise/util/exercise-form-state';

describe('ExerciseFormState', () => {
    const exerciseWithPoints = (maxPoints?: number) => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.maxPoints = maxPoints;
        return exercise;
    };

    it('should hold nothing until an exercise is set, unless it is given one to start with', () => {
        const initial = exerciseWithPoints(1);

        expect(new ExerciseFormState<TextExercise>().exercise()).toBeUndefined();
        expect(new ExerciseFormState(initial).exercise()).toBe(initial);
    });

    it('should notify a computed when a field is patched, and keep the object it holds', () => {
        const exercise = exerciseWithPoints();
        const state = new ExerciseFormState(exercise);
        const points = computed(() => state.exercise().maxPoints);
        expect(points()).toBeUndefined();

        state.patch('maxPoints', 5);

        expect(points()).toBe(5);
        expect(state.exercise()).toBe(exercise);
        expect(exercise.maxPoints).toBe(5);
    });

    it('should notify a computed when the exercise is changed in place through update', () => {
        const exercise = exerciseWithPoints(1);
        const state = new ExerciseFormState(exercise);
        const points = computed(() => state.exercise().maxPoints);
        expect(points()).toBe(1);

        state.update((edited) => (edited.maxPoints = 2));

        expect(points()).toBe(2);
        expect(state.exercise()).toBe(exercise);
    });

    it('should notify when a child component announces a change to the same object', () => {
        const exercise = exerciseWithPoints(1);
        const state = new ExerciseFormState(exercise);
        const points = computed(() => state.exercise().maxPoints);
        expect(points()).toBe(1);

        // What a child component does: write in place, then hand the same object back through its exerciseChange output.
        exercise.maxPoints = 3;
        state.set(exercise);

        expect(points()).toBe(3);
    });

    it('should not notify a computed for a write that bypasses it', () => {
        const exercise = exerciseWithPoints(1);
        const state = new ExerciseFormState(exercise);
        const points = computed(() => state.exercise().maxPoints);
        expect(points()).toBe(1);

        // The write this class exists to replace: nothing tells the computed, so it keeps the old value.
        state.exercise().maxPoints = 4;

        expect(points()).toBe(1);
    });

    it('should replace the exercise when set with another one', () => {
        const state = new ExerciseFormState(exerciseWithPoints(1));
        const replacement = exerciseWithPoints(2);

        state.set(replacement);

        expect(state.exercise()).toBe(replacement);
    });

    it('should not make an effect that calls update depend on the exercise', () => {
        const state = new ExerciseFormState(exerciseWithPoints(0));
        let runs = 0;
        runInInjectionContext(TestBed.inject(Injector), () =>
            effect(() => {
                runs++;
                state.update((exercise) => (exercise.maxPoints = (exercise.maxPoints ?? 0) + 1));
            }),
        );

        TestBed.tick();
        state.patch('title', 'Changed');
        TestBed.tick();

        // Once, not again for its own write or for the later patch: update reads the exercise untracked.
        expect(runs).toBe(1);
        expect(state.exercise().maxPoints).toBe(1);
    });
});

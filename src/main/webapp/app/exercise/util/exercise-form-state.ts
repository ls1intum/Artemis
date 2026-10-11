import { Signal, WritableSignal, signal, untracked } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

/**
 * The exercise an update form edits, held in a signal so that the form's derived state can be a `computed()`.
 *
 * Fields are written in place on purpose. The child components of the form hold the same object, and its nested
 * associations (grading criteria, team assignment config, competency links, ...) must keep their identity: a copy per edit
 * would hand every child a new `exercise` input on each keystroke, re-run what they derive from it (a request for the
 * titles already used in the course, for one) and re-create the rows they track by identity. The signal is therefore
 * declared with `equal: () => false`, so every write through this class notifies although the reference stays the same.
 *
 * Every change to the exercise has to go through {@link set}, {@link update} or {@link patch}. A write that bypasses them
 * (`exercise.maxPoints = 3` on the object the signal returned) changes the object without telling anyone, and the derived
 * state keeps showing the value from before. A child component that writes to the exercise announces it with an
 * `exerciseChange` output, which the form binds as `[(exercise)]` to a setter that calls {@link set}.
 */
export class ExerciseFormState<T extends Exercise> {
    private readonly state: WritableSignal<T>;

    /** The edited exercise; undefined until the form has loaded it, unless an initial exercise was passed. */
    readonly exercise: Signal<T>;

    constructor(initialExercise?: T) {
        this.state = signal<T>(initialExercise!, { equal: () => false });
        this.exercise = this.state.asReadonly();
    }

    /** Replaces the edited exercise, or announces a change a child component made to the same object. */
    set(exercise: T): void {
        this.state.set(exercise);
    }

    /**
     * Changes the edited exercise in place and notifies. The exercise is read untracked, so calling this from an effect
     * does not make the effect depend on the exercise.
     */
    update(mutate: (exercise: T) => void): void {
        const exercise = untracked(this.state);
        mutate(exercise);
        this.state.set(exercise);
    }

    /** Sets one field of the edited exercise and notifies; meant for template bindings. */
    patch<K extends keyof T>(field: K, value: T[K]): void {
        this.update((exercise) => (exercise[field] = value));
    }
}

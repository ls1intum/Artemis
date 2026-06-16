import { Component, computed, inject, input, output, viewChild } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { EMPTY } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { Course, isCommunicationEnabled } from 'app/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TitleChannelNameComponent } from 'app/shared-ui/form/title-channel-name/title-channel-name.component';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { ExerciseService } from 'app/exercise/services/exercise.service';

/**
 * @deprecated Use {@link ExerciseTitleChannelNamePrimengComponent} instead.
 */
@Component({
    selector: 'jhi-exercise-title-channel-name',
    templateUrl: './exercise-title-channel-name.component.html',
    imports: [TitleChannelNameComponent],
})
export class ExerciseTitleChannelNameComponent {
    private readonly exerciseService = inject(ExerciseService);

    readonly course = input<Course>();
    readonly isEditFieldDisplayedRecord = input<Record<ProgrammingExerciseInputField, boolean>>();
    readonly courseId = input<number>();

    readonly exercise = input<Exercise>({} as Exercise);
    readonly titlePattern = input<string>();
    readonly minTitleLength = input<number>();
    readonly isExamMode = input<boolean>(false);
    readonly isImport = input<boolean>(false);
    readonly hideTitleLabel = input<boolean>(false);
    readonly hideChannelNameLabel = input<boolean>(false);
    readonly titleHelpIconText = input<string>();
    readonly channelNameHelpIconText = input<string>('');

    readonly titleChannelNameComponent = viewChild.required(TitleChannelNameComponent);

    readonly onTitleChange = output<string>();
    readonly onChannelNameChange = output<string>();

    /**
     * Titles already used in the course (to validate uniqueness). Replaces the former
     * `fetchExistingExerciseNamesOnInit` effect (a data fetch in an effect() with a leaking subscription): the names
     * are derived from courseId + exercise type via a switchMap'd stream. While the key is incomplete (or on error) the
     * stream stays silent (EMPTY), so the previously loaded names are kept — matching the former "only fetch when both
     * are present" behaviour — and toSignal owns the subscription lifecycle.
     */
    readonly alreadyUsedExerciseNames = toSignal(
        toObservable(computed(() => ({ courseId: this.courseId() ?? this.course()?.id, type: this.exercise().type }))).pipe(
            switchMap(({ courseId, type }) =>
                courseId && type
                    ? this.exerciseService.getExistingExerciseDetailsInCourse(courseId, type).pipe(
                          map((exerciseDetails) => exerciseDetails.exerciseTitles ?? new Set<string>()),
                          catchError(() => EMPTY),
                      )
                    : EMPTY,
            ),
        ),
        { initialValue: new Set<string>() },
    );

    readonly hideChannelNameInput = computed(() => !this.requiresChannelName(this.exercise(), this.course(), this.isExamMode(), this.isImport()));

    updateTitle(newTitle: string | undefined) {
        this.exercise().title = newTitle;
        this.onTitleChange.emit(newTitle ?? '');
    }

    updateChannelName(newName: string | undefined) {
        this.exercise().channelName = newName;
        this.onChannelNameChange.emit(newName ?? '');
    }

    /**
     * Determines whether the provided exercises should have a channel name. This is not the case, if messaging in the course
     * is disabled or if it is an exam exercise.
     * If messaging is enabled, a channel name should exist for newly created and imported exercises.
     *
     * @param exercise      the exercise under consideration
     * @param course        the current course context (might differ from the exercise in case of import)
     * @param isExamMode    true if the exercise should be an exam exercise
     * @param isImport      true if the exercise is being imported
     * @return boolean      true if the channel name is required, else false
     */
    private requiresChannelName(exercise: Exercise, course: Course | undefined, isExamMode: boolean, isImport: boolean): boolean {
        // not required if messaging is disabled or exam mode
        if (!isCommunicationEnabled(course) || isExamMode) {
            return false;
        }

        // required on create or import (messaging is enabled)
        const isCreate = exercise.id === undefined;
        if (isCreate || isImport) {
            return true;
        }

        // when editing, it is required if the exercise has a channel
        return exercise.channelName !== undefined;
    }
}

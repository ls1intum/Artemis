import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild, effect, inject, input, signal } from '@angular/core';
import { Course, isCommunicationEnabled } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { TitleChannelNameComponent } from 'app/shared/form/title-channel-name/title-channel-name.component';
import { ProgrammingExerciseInputField } from 'app/exercises/programming/manage/update/programming-exercise-update.helper';
import { CourseExistingExerciseDetailsType, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-exercise-title-channel-name',
    templateUrl: './exercise-title-channel-name.component.html',
})
export class ExerciseTitleChannelNameComponent implements OnChanges {
    @Input() exercise: Exercise;
    course = input<Course>();
    @Input() titlePattern: string;
    @Input() minTitleLength: number;
    @Input() isExamMode: boolean;
    @Input() isImport: boolean;
    @Input() hideTitleLabel: boolean;
    isEditFieldDisplayedRecord = input<Record<ProgrammingExerciseInputField, boolean>>();
    courseId = input<number>();

    @ViewChild(TitleChannelNameComponent) titleChannelNameComponent: TitleChannelNameComponent;

    @Output() onTitleChange = new EventEmitter<string>();
    @Output() onChannelNameChange = new EventEmitter<string>();

    private readonly exerciseService: ExerciseService = inject(ExerciseService);

    alreadyUsedExerciseNames = signal<Set<string>>(new Set());

    hideChannelNameInput = false;

    constructor() {
        effect(
            function fetchExistingExerciseNamesOnInit() {
                const courseId = this.courseId() ?? this.course()?.id;
                if (courseId && this.exercise.type) {
                    this.exerciseService.getExistingExerciseDetailsInCourse(courseId, this.exercise.type).subscribe((exerciseDetails: CourseExistingExerciseDetailsType) => {
                        this.alreadyUsedExerciseNames.set(exerciseDetails.exerciseTitles ?? new Set());
                    });
                }
            }.bind(this),
            { allowSignalWrites: true },
        );
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.exercise || changes.course || changes.isExamMode || this.isImport) {
            this.hideChannelNameInput = !this.requiresChannelName(this.exercise, this.course(), this.isExamMode, this.isImport);
        }
    }

    updateTitle(newTitle: string) {
        this.exercise.title = newTitle;
        this.onTitleChange.emit(newTitle);
    }

    updateChannelName(newName: string) {
        this.exercise.channelName = newName;
        this.onChannelNameChange.emit(newName);
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

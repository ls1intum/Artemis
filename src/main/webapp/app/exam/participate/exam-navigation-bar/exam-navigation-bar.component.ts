import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';
import * as moment from 'moment';
import { Moment } from 'moment';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { LiveExamExerciseUpdateService } from 'app/exam/live-exam-exercise-update.service';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-exam-navigation-bar',
    templateUrl: './exam-navigation-bar.component.html',
    styleUrls: ['./exam-navigation-bar.component.scss'],
})
export class ExamNavigationBarComponent implements OnInit {
    @Input() exercises: Exercise[] = [];
    @Input() exerciseIndex = 0;
    @Input() endDate: Moment;

    @Output() onExerciseChanged = new EventEmitter<{ exercise: Exercise; forceSave: boolean }>();
    @Output() examAboutToEnd = new EventEmitter<void>();
    @Output() onExamHandInEarly = new EventEmitter<void>();

    static itemsVisiblePerSideDefault = 4;
    itemsVisiblePerSide = ExamNavigationBarComponent.itemsVisiblePerSideDefault;

    criticalTime = moment.duration(5, 'minutes');

    icon: IconProp;

    subscriptionToLiveExamExerciseUpdates: Subscription;

    constructor(private layoutService: LayoutService, private liveExamExerciseUpdateService: LiveExamExerciseUpdateService) {}

    ngOnInit(): void {
        this.subscriptionToLiveExamExerciseUpdates = this.liveExamExerciseUpdateService.currentExerciseIdAndProblemStatement.subscribe((update) => {
            if (update.problemStatement === '') {
                this.changeExerciseById(update.exerciseId);
            } else {
                this.updateExerciseProblemStatementById(update.exerciseId, update.problemStatement);
            }
        });

        this.layoutService.subscribeToLayoutChanges().subscribe(() => {
            // You will have all matched breakpoints in observerResponse
            if (this.layoutService.isBreakpointActive(CustomBreakpointNames.extraLarge)) {
                this.itemsVisiblePerSide = ExamNavigationBarComponent.itemsVisiblePerSideDefault;
            } else if (this.layoutService.isBreakpointActive(CustomBreakpointNames.large)) {
                this.itemsVisiblePerSide = 3;
            } else if (this.layoutService.isBreakpointActive(CustomBreakpointNames.medium)) {
                this.itemsVisiblePerSide = 1;
            } else {
                this.itemsVisiblePerSide = 0;
            }
        });
    }

    triggerExamAboutToEnd() {
        this.saveExercise(false);
        this.examAboutToEnd.emit();
    }

    /**
     * Changes the currently visible exercise
     * @param exerciseIndex of the student exam to navigate to / to display
     * @param forceSave whether to save the exercise before displaying another or not
     */
    changeExercise(exerciseIndex: number, forceSave: boolean) {
        // out of index -> do nothing
        if (exerciseIndex > this.exercises.length - 1 || exerciseIndex < 0) {
            return;
        }
        // set index and emit event
        this.exerciseIndex = exerciseIndex;
        this.onExerciseChanged.emit({ exercise: this.exercises[exerciseIndex], forceSave });
        this.setExerciseButtonStatus(exerciseIndex);
    }

    /**
     * Auxiliary method to call changeExercise based on the unique id of the exercise (e.g. exerciseA.id -> 7)
     * The exercises in a student exam can be ordered randomly and be from different exercise groups
     * this means that the identical exercise A can have a different position/index inside two different student exams
     * i.e. exercise A can be the second exercise in student exam 1 but the third in student exam 2
     * @param exerciseId the unique identifier of an exercise that stays the same regardless of student exam ordering
     */
    changeExerciseById(exerciseId: number) {
        const foundIndex = this.exercises.findIndex((ex) => ex.id === exerciseId);
        this.changeExercise(foundIndex, true);
    }

    /**
     * Updates the problem statement of an exam exercise during an ongoing exam in real time,
     * i.e. the student will see the change immediately without the need to reload the page
     * @param exerciseId the unique exercise that needs to be updated
     * @param problemStatement the updated problem statement
     */
    updateExerciseProblemStatementById(exerciseId: number, problemStatement: string) {
        if (exerciseId !== -1 && problemStatement != undefined) {
            const foundIndex = this.exercises.findIndex((ex) => ex.id === exerciseId);
            this.exercises[foundIndex].problemStatement = problemStatement;
        }
    }

    /**
     * Save the currently active exercise and go to the next exercise.
     * @param changeExercise whether to go to the next exercise {boolean}
     */
    saveExercise(changeExercise = true) {
        const newIndex = this.exerciseIndex + 1;
        const submission = this.getSubmissionForExercise(this.exercises[this.exerciseIndex]);
        // we do not submit programming exercises on a save
        if (submission && this.exercises[this.exerciseIndex].type !== ExerciseType.PROGRAMMING) {
            submission.submitted = true;
        }
        if (changeExercise) {
            if (newIndex > this.exercises.length - 1) {
                // we are in the last exercise, if out of range "change" active exercise to current in order to trigger a save
                this.changeExercise(this.exerciseIndex, true);
            } else {
                this.changeExercise(newIndex, true);
            }
        }
    }

    isProgrammingExercise() {
        return this.exercises[this.exerciseIndex].type === ExerciseType.PROGRAMMING;
    }

    isFileUploadExercise() {
        return this.exercises[this.exerciseIndex].type === ExerciseType.FILE_UPLOAD;
    }

    setExerciseButtonStatus(exerciseIndex: number): 'synced' | 'synced active' | 'notSynced' {
        this.icon = 'edit';
        const exercise = this.exercises[exerciseIndex];
        const submission = this.getSubmissionForExercise(exercise);
        if (submission) {
            if (submission.submitted) {
                this.icon = 'check';
            }
            if (submission.isSynced) {
                // make button blue
                if (exerciseIndex === this.exerciseIndex) {
                    return 'synced active';
                } else {
                    return 'synced';
                }
            } else {
                // make button yellow
                this.icon = 'edit';
                return 'notSynced';
            }
        } else {
            // in case no participation yet exists -> display synced
            return 'synced';
        }
    }

    getExerciseButtonTooltip(exerciseIndex: number): 'submitted' | 'notSubmitted' | 'synced' | 'notSynced' | 'notSavedOrSubmitted' {
        const submission = this.getSubmissionForExercise(this.exercises[exerciseIndex]);
        if (submission) {
            if (this.exercises[exerciseIndex].type === ExerciseType.PROGRAMMING) {
                if (submission.submitted && submission.isSynced) {
                    return 'submitted'; // You have submitted an exercise. You can submit again
                } else if (submission.submitted && !submission.isSynced) {
                    return 'notSavedOrSubmitted'; // You have unsaved and/or unsubmitted changes
                } else if (!submission.submitted && submission.isSynced) {
                    return 'notSubmitted'; // starting point
                } else {
                    return 'notSavedOrSubmitted';
                }
            } else {
                if (submission.isSynced) {
                    return 'synced';
                } else {
                    return 'notSynced';
                }
            }
        } else {
            // submission does not yet exist for this exercise.
            // When the participant navigates to the exercise the submissions are created.
            // Until then show, that the exercise is synced
            return 'synced';
        }
    }

    /**
     * Notify parent component when user wants to hand in early
     */
    handInEarly() {
        this.saveExercise(false);
        this.onExamHandInEarly.emit();
    }

    // TODO: find usages of similar logic -> put into utils method
    getSubmissionForExercise(exercise: Exercise) {
        if (
            exercise &&
            exercise.studentParticipations &&
            exercise.studentParticipations.length > 0 &&
            exercise.studentParticipations[0].submissions &&
            exercise.studentParticipations[0].submissions.length > 0
        ) {
            return exercise.studentParticipations[0].submissions[0];
        } else {
            return undefined;
        }
    }
}

import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';
import dayjs from 'dayjs/esm';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { Subscription } from 'rxjs';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { CommitState, DomainChange, DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorRepositoryService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { map } from 'rxjs/operators';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { ExamSession } from 'app/entities/exam-session.model';
import { faBars, faCheck, faEdit } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-exam-navigation-bar',
    templateUrl: './exam-navigation-bar.component.html',
    styleUrls: ['./exam-navigation-bar.component.scss'],
})
export class ExamNavigationBarComponent implements OnInit {
    @Input() exercises: Exercise[] = [];
    @Input() exerciseIndex = 0;
    @Input() endDate: dayjs.Dayjs;
    @Input() overviewPageOpen: boolean;
    @Input() examSessions?: ExamSession[] = [];
    @Output() onPageChanged = new EventEmitter<{ overViewChange: boolean; exercise?: Exercise; forceSave: boolean }>();
    @Output() examAboutToEnd = new EventEmitter<void>();
    @Output() onExamHandInEarly = new EventEmitter<void>();

    static itemsVisiblePerSideDefault = 4;
    itemsVisiblePerSide = ExamNavigationBarComponent.itemsVisiblePerSideDefault;

    criticalTime = dayjs.duration(5, 'minutes');

    icon: IconProp;
    getExerciseButtonTooltip = this.examParticipationService.getExerciseButtonTooltip;

    subscriptionToLiveExamExerciseUpdates: Subscription;

    // Icons
    faBars = faBars;

    constructor(
        private layoutService: LayoutService,
        private examParticipationService: ExamParticipationService,
        private examExerciseUpdateService: ExamExerciseUpdateService,
        private repositoryService: CodeEditorRepositoryService,
        private conflictService: CodeEditorConflictStateService,
    ) {}

    ngOnInit(): void {
        this.subscriptionToLiveExamExerciseUpdates = this.examExerciseUpdateService.currentExerciseIdForNavigation.subscribe((exerciseIdToNavigateTo) => {
            // another exercise will only be displayed if the student clicks on the corresponding pop-up notification
            this.changeExerciseById(exerciseIdToNavigateTo);
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

        const isInitialSession = this.examSessions && this.examSessions.length > 0 && this.examSessions[0].initialSession;
        if (isInitialSession || isInitialSession == undefined) {
            return;
        }

        // If it is not an initial session, update the isSynced variable for out of sync submissions.
        this.exercises
            .filter((exercise) => exercise.type === ExerciseType.PROGRAMMING && exercise.studentParticipations)
            .forEach((exercise) => {
                const domain: DomainChange = [DomainType.PARTICIPATION, exercise.studentParticipations![0]];
                this.conflictService.setDomain(domain);
                this.repositoryService.setDomain(domain);

                this.repositoryService
                    .getStatus()
                    .pipe(map((response) => Object.values(CommitState).find((commitState) => commitState === response.repositoryStatus)))
                    .subscribe((commitState) => {
                        const submission = ExamParticipationService.getSubmissionForExercise(exercise);
                        if (commitState === CommitState.UNCOMMITTED_CHANGES && submission) {
                            // If there are uncommitted changes: set isSynced to false.
                            submission.isSynced = false;
                        }
                    });
            });
    }

    triggerExamAboutToEnd() {
        this.saveExercise(false);
        this.examAboutToEnd.emit();
    }

    /**
     * @param overviewPage: user wants to switch to the overview page
     * @param exerciseIndex: index of the exercise to switch to, if it should not be used, you can pass -1
     * @param forceSave: true if forceSave shall be used.
     */
    changePage(overviewPage: boolean, exerciseIndex: number, forceSave?: boolean): void {
        if (!overviewPage) {
            // out of index -> do nothing
            if (exerciseIndex > this.exercises.length - 1 || exerciseIndex < 0) {
                return;
            }
            // set index and emit event
            this.exerciseIndex = exerciseIndex;
            this.onPageChanged.emit({ overViewChange: false, exercise: this.exercises[this.exerciseIndex], forceSave: !!forceSave });
        } else if (overviewPage) {
            // set index and emit event
            this.exerciseIndex = -1;
            // save current exercise
            this.onPageChanged.emit({ overViewChange: true, exercise: undefined, forceSave: false });
        }
        this.setExerciseButtonStatus(this.exerciseIndex);
    }

    /**
     * Auxiliary method to call changeExerciseByIndex based on the unique id of the exercise
     * @param exerciseId the unique identifier of an exercise that stays the same regardless of student exam ordering
     */
    changeExerciseById(exerciseId: number) {
        const foundIndex = this.exercises.findIndex((exercise) => exercise.id === exerciseId);
        this.changePage(false, foundIndex, true);
    }

    /**
     * Save the currently active exercise and go to the next exercise.
     * @param changeExercise whether to go to the next exercise {boolean}
     */
    saveExercise(changeExercise = true) {
        const newIndex = this.exerciseIndex + 1;
        const submission = ExamParticipationService.getSubmissionForExercise(this.exercises[this.exerciseIndex]);
        // we do not submit programming exercises on a save
        if (submission && this.exercises[this.exerciseIndex].type !== ExerciseType.PROGRAMMING) {
            submission.submitted = true;
        }
        if (changeExercise) {
            if (newIndex > this.exercises.length - 1) {
                // we are in the last exercise, if out of range "change" active exercise to current in order to trigger a save
                this.changePage(false, this.exerciseIndex, true);
            } else {
                this.changePage(false, newIndex, true);
            }
        }
    }

    isProgrammingExercise() {
        return this.exercises[this.exerciseIndex].type === ExerciseType.PROGRAMMING;
    }

    isFileUploadExercise() {
        return this.exercises[this.exerciseIndex].type === ExerciseType.FILE_UPLOAD;
    }

    getOverviewStatus(): 'active' | '' {
        return this.overviewPageOpen ? 'active' : '';
    }

    /**
     * calculate the exercise status (also see exam-exercise-overview-page.component.ts --> make sure the logic is consistent)
     * also determines the used icon and its color
     * TODO: we should try to extract a method for the common logic which avoids side effects (i.e. changing this.icon)
     *  this method could e.g. return the sync status and the icon
     *
     * @param exerciseIndex index of the exercise
     * @return the sync status of the exercise (whether the corresponding submission is saved on the server or not)
     */
    setExerciseButtonStatus(exerciseIndex: number): 'synced' | 'synced active' | 'notSynced' {
        // start with a yellow status (edit icon)
        // TODO: it's a bit weired, that it works that multiple icons (one per exercise) are hold in the same instance variable of the component
        //  we should definitely refactor this and e.g. use the same ExamExerciseOverviewItem as in exam-exercise-overview-page.component.ts !
        this.icon = faEdit;
        const exercise = this.exercises[exerciseIndex];
        const submission = ExamParticipationService.getSubmissionForExercise(exercise);
        if (!submission) {
            // in case no participation/submission yet exists -> display synced
            return 'synced';
        }
        if (submission.submitted) {
            this.icon = faCheck;
        }
        if (submission.isSynced) {
            // make button blue (except for the current page)
            if (exerciseIndex === this.exerciseIndex && !this.overviewPageOpen) {
                return 'synced active';
            } else {
                return 'synced';
            }
        } else {
            // make button yellow
            this.icon = faEdit;
            return 'notSynced';
        }
    }

    /**
     * Notify parent component when user wants to hand in early
     */
    handInEarly() {
        this.onExamHandInEarly.emit();
    }
}

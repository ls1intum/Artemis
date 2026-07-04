import { AfterViewInit, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { LayoutService } from 'app/foundation/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/foundation/breakpoints/breakpoints.service';
import dayjs from 'dayjs/esm';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExamExerciseUpdateService } from 'app/exam/manage/services/exam-exercise-update.service';
import { Subscription } from 'rxjs';
import { ButtonTooltipType, ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { CommitState, DomainChange, DomainType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { map } from 'rxjs/operators';
import { CodeEditorConflictStateService } from 'app/programming/shared/code-editor/services/code-editor-conflict-state.service';
import { ExamSession } from 'app/exam/shared/entities/exam-session.model';
import { faBars, faCheck, faEdit } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExamLiveEventsButtonComponent } from '../events/button/exam-live-events-button.component';
import { NgClass } from '@angular/common';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExamTimerComponent } from '../timer/exam-timer.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';

@Component({
    selector: 'jhi-exam-navigation-bar',
    templateUrl: './exam-navigation-bar.component.html',
    styleUrls: ['./exam-navigation-bar.component.scss'],
    imports: [TranslateDirective, ExamLiveEventsButtonComponent, NgClass, NgbTooltip, FaIconComponent, ExamTimerComponent, ArtemisTranslatePipe],
})
export class ExamNavigationBarComponent implements OnInit, AfterViewInit {
    private layoutService = inject(LayoutService);
    private examParticipationService = inject(ExamParticipationService);
    private examExerciseUpdateService = inject(ExamExerciseUpdateService);
    private repositoryService = inject(CodeEditorRepositoryService);
    private conflictService = inject(CodeEditorConflictStateService);

    readonly exercises = input<Exercise[]>([]);
    readonly exerciseIndex = input(0);
    readonly endDate = input<dayjs.Dayjs>(undefined!);
    readonly overviewPageOpen = input<boolean>(undefined!);
    readonly examSessions = input<ExamSession[] | undefined>([]);
    readonly examTimeLineView = input(false);
    readonly onPageChanged = output<{
        overViewChange: boolean;
        exercise?: Exercise;
        forceSave: boolean;
        submission?: ProgrammingSubmission | SubmissionVersion | FileUploadSubmission;
    }>();
    readonly examAboutToEnd = output<void>();
    readonly onExamHandInEarly = output<void>();

    static itemsVisiblePerSideDefault = 4;
    readonly itemsVisiblePerSide = signal(ExamNavigationBarComponent.itemsVisiblePerSideDefault);

    criticalTime = dayjs.duration(5, 'minutes');

    readonly icon = signal<IconProp>(faCheck);

    subscriptionToLiveExamExerciseUpdates: Subscription;

    // Icons
    faBars = faBars;

    ngOnInit(): void {
        if (!this.examTimeLineView()) {
            this.subscriptionToLiveExamExerciseUpdates = this.examExerciseUpdateService.currentExerciseIdForNavigation.subscribe((exerciseIdToNavigateTo) => {
                // another exercise will only be displayed if the student clicks on the corresponding pop-up notification
                this.changeExerciseById(exerciseIdToNavigateTo);
            });
        }

        this.layoutService.subscribeToLayoutChanges().subscribe(() => {
            // You will have all matched breakpoints in observerResponse
            if (this.layoutService.isBreakpointActive(CustomBreakpointNames.extraLarge)) {
                this.itemsVisiblePerSide.set(ExamNavigationBarComponent.itemsVisiblePerSideDefault);
            } else if (this.layoutService.isBreakpointActive(CustomBreakpointNames.large)) {
                this.itemsVisiblePerSide.set(3);
            } else if (this.layoutService.isBreakpointActive(CustomBreakpointNames.medium)) {
                this.itemsVisiblePerSide.set(1);
            } else {
                this.itemsVisiblePerSide.set(0);
            }
        });

        const examSessions = this.examSessions();
        const isInitialSession = examSessions && examSessions.length > 0 && examSessions[0].initialSession;
        if (isInitialSession || isInitialSession == undefined) {
            return;
        }

        // If it is not an initial session, update the isSynced variable for out of sync submissions.
        this.exercises()
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

    ngAfterViewInit() {
        // Use setTimeout to ensure the DOM is fully loaded before calculating headerHeight
        setTimeout(() => {
            const headerHeight = (document.querySelector('jhi-navbar') as HTMLElement)?.offsetHeight;
            document.documentElement.style.setProperty('--header-height', `${headerHeight}px`);
        });
    }

    getExerciseButtonTooltip(exercise: Exercise): ButtonTooltipType {
        return this.examParticipationService.getExerciseButtonTooltip(exercise);
    }

    triggerExamAboutToEnd() {
        this.saveExercise(false);
        this.examAboutToEnd.emit();
    }

    /**
     * @param overviewPage user wants to switch to the overview page
     * @param exerciseIndex index of the exercise to switch to, if it should not be used, you can pass -1
     * @param forceSave true if forceSave shall be used.
     * @param submission the submission to be viewed, used in the exam timeline
     */
    changePage(overviewPage: boolean, exerciseIndex: number, forceSave?: boolean, submission?: SubmissionVersion | ProgrammingSubmission | FileUploadSubmission): void {
        if (!overviewPage) {
            if (exerciseIndex > this.exercises().length - 1 || exerciseIndex < 0) {
                return;
            }
            this.onPageChanged.emit({ overViewChange: false, exercise: this.exercises()[exerciseIndex], forceSave: !!forceSave, submission: submission });
            this.setExerciseButtonStatus(exerciseIndex);
        } else {
            this.onPageChanged.emit({ overViewChange: true, exercise: undefined, forceSave: false });
            this.setExerciseButtonStatus(-1);
        }
    }

    /**
     * Auxiliary method to call changeExerciseByIndex based on the unique id of the exercise
     * @param exerciseId the unique identifier of an exercise that stays the same regardless of student exam ordering
     */
    changeExerciseById(exerciseId: number) {
        const foundIndex = this.exercises().findIndex((exercise) => exercise.id === exerciseId);
        this.changePage(false, foundIndex, true);
    }

    /**
     * Save the currently active exercise and go to the next exercise.
     * @param changeExercise whether to go to the next exercise {boolean}
     */
    saveExercise(changeExercise = true) {
        const newIndex = this.exerciseIndex() + 1;
        const submission = ExamParticipationService.getSubmissionForExercise(this.exercises()[this.exerciseIndex()]);
        // we do not submit programming exercises on a save
        const exercises = this.exercises();
        const exerciseIndex = this.exerciseIndex();
        if (submission && exercises[exerciseIndex].type !== ExerciseType.PROGRAMMING) {
            submission.submitted = true;
        }
        if (changeExercise) {
            if (newIndex > exercises.length - 1) {
                // we are in the last exercise, if out of range "change" active exercise to current in order to trigger a save
                this.changePage(false, exerciseIndex, true);
            } else {
                this.changePage(false, newIndex, true);
            }
        }
    }

    isProgrammingExercise() {
        return this.exercises()[this.exerciseIndex()].type === ExerciseType.PROGRAMMING;
    }

    isFileUploadExercise() {
        return this.exercises()[this.exerciseIndex()].type === ExerciseType.FILE_UPLOAD;
    }

    getOverviewStatus(): 'active' | '' {
        return this.overviewPageOpen() ? 'active' : '';
    }

    /**
     * Pure computation of the exercise button status and its icon, free of side effects so it can be
     * called safely during template rendering (writing to a signal during render throws NG0600).
     *
     * @param exerciseIndex index of the exercise
     * @return the sync status of the exercise and the icon to display for it
     */
    computeExerciseButtonStatus(exerciseIndex: number): { status: 'synced' | 'synced active' | 'notSynced'; icon: IconProp } {
        // If we are in the exam timeline we do not use not synced as not synced shows
        // that the current submission is not saved which doesn't make sense in the timeline.
        if (this.examTimeLineView()) {
            return { status: this.exerciseIndex() === exerciseIndex ? 'synced active' : 'synced', icon: faCheck };
        }

        // start with a yellow status (edit icon)
        const exercise = this.exercises()[exerciseIndex];
        const submission = ExamParticipationService.getSubmissionForExercise(exercise);
        if (!submission) {
            // in case no participation/submission yet exists -> display synced
            // this should only occur for programming exercises
            return { status: 'synced', icon: faEdit };
        }
        const icon: IconProp = submission.submitted ? faCheck : faEdit;
        if (submission.isSynced || this.isOnlyOfflineIDE(exercise)) {
            // make button blue (except for the current page)
            if (exerciseIndex === this.exerciseIndex() && !this.overviewPageOpen()) {
                return { status: 'synced active', icon };
            } else {
                return { status: 'synced', icon };
            }
        } else {
            // make button yellow except for programming exercises with only offline IDE
            return { status: 'notSynced', icon: faEdit };
        }
    }

    /**
     * Pure helper for the template: returns the icon to display for the given exercise without mutating state.
     */
    getExerciseButtonIcon(exerciseIndex: number): IconProp {
        return this.computeExerciseButtonStatus(exerciseIndex).icon;
    }

    /**
     * calculate the exercise status (also see exam-exercise-overview-page.component.ts --> make sure the logic is consistent)
     * also determines the used icon and its color and stores it in the {@link icon} signal.
     *
     * @param exerciseIndex index of the exercise
     * @return the sync status of the exercise (whether the corresponding submission is saved on the server or not)
     */
    setExerciseButtonStatus(exerciseIndex: number): 'synced' | 'synced active' | 'notSynced' {
        const { status, icon } = this.computeExerciseButtonStatus(exerciseIndex);
        this.icon.set(icon);
        return status;
    }

    isOnlyOfflineIDE(exercise: Exercise): boolean {
        if (exercise instanceof ProgrammingExercise) {
            const programmingExercise = exercise;
            return programmingExercise.allowOfflineIde === true && programmingExercise.allowOnlineEditor === false;
        }
        return false;
    }

    handInEarly() {
        this.onExamHandInEarly.emit();
    }
}

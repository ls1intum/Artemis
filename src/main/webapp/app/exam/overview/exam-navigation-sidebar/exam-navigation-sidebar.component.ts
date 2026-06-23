import { Component, HostListener, OnDestroy, OnInit, inject, input, output, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { SidebarEventService } from 'app/course/sidebar/service/sidebar-event.service';
import { ExamSession } from 'app/exam/shared/entities/exam-session.model';
import { Exercise, ExerciseType, getIconTooltip } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { ExamExerciseUpdateService } from 'app/exam/manage/services/exam-exercise-update.service';
import { ButtonTooltipType, ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { map } from 'rxjs/operators';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faChevronRight, faFileLines, faHourglassHalf } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { CodeEditorConflictStateService } from 'app/programming/shared/code-editor/services/code-editor-conflict-state.service';
import { CommitState, DomainChange, DomainType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { SidebarData } from 'app/foundation/types/sidebar';
import { facSaveSuccess, facSaveWarning } from 'app/foundation/icons/icons';
import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';

export enum ExerciseButtonStatus {
    Synced = 'synced',
    SyncedSaved = 'synced saved',
    NotSynced = 'notSynced',
}

@Component({
    selector: 'jhi-exam-navigation-sidebar',
    imports: [ArtemisTranslatePipe, CommonModule, FontAwesomeModule, NgbTooltipModule, TranslateDirective],
    templateUrl: './exam-navigation-sidebar.component.html',
    styleUrl: './exam-navigation-sidebar.component.scss',
})
export class ExamNavigationSidebarComponent implements OnDestroy, OnInit {
    private sidebarEventService = inject(SidebarEventService);
    private examParticipationService = inject(ExamParticipationService);
    private examExerciseUpdateService = inject(ExamExerciseUpdateService);
    private repositoryService = inject(CodeEditorRepositoryService);
    private conflictService = inject(CodeEditorConflictStateService);

    readonly sidebarData = input<SidebarData>(undefined!);
    readonly exercises = input<Exercise[]>([]);
    readonly exerciseIndex = input(0);
    readonly overviewPageOpen = input<boolean>(undefined!);
    readonly examSessions = input<ExamSession[] | undefined>([]);
    readonly examTimeLineView = input(false);
    readonly isTestRun = input(0);
    readonly onPageChanged = output<{
        overViewChange: boolean;
        exercise?: Exercise;
        forceSave: boolean;
        submission?: ProgrammingSubmission | SubmissionVersion | FileUploadSubmission;
    }>();

    /**
     * Index indicating that the content is exercise overview
     */
    readonly EXERCISE_OVERVIEW_INDEX = -1;
    subscriptionToLiveExamExerciseUpdates?: Subscription;

    // Icons
    readonly faFileLines = faFileLines;
    readonly faChevronRight = faChevronRight;

    readonly isCollapsed = signal(false);
    exerciseId: string;
    // Bumped whenever submission sync state is mutated in place (async callbacks) so the pure
    // template methods below re-evaluate under zoneless change detection.
    private readonly syncStateVersion = signal(0);

    ngOnInit(): void {
        if (!this.examTimeLineView()) {
            this.subscriptionToLiveExamExerciseUpdates = this.examExerciseUpdateService.currentExerciseIdForNavigation.subscribe((exerciseIdToNavigateTo) => {
                // another exercise will only be displayed if the student clicks on the corresponding pop-up notification
                this.changeExerciseById(exerciseIdToNavigateTo);
            });
        }

        // TODO: avoid duplicated code
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
                            this.syncStateVersion.update((version) => version + 1);
                        }
                    });
            });
    }

    ngOnDestroy() {
        this.subscriptionToLiveExamExerciseUpdates?.unsubscribe();
        this.sidebarEventService.emitResetValue();
    }

    getExerciseButtonTooltip(exercise: Exercise): ButtonTooltipType {
        return this.examParticipationService.getExerciseButtonTooltip(exercise);
    }

    getExerciseIconTooltip(exercise: Exercise): string {
        return getIconTooltip(exercise.type);
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
            this.onPageChanged.emit({
                overViewChange: false,
                exercise: this.exercises()[exerciseIndex],
                forceSave: !!forceSave,
                submission: submission,
            });
        } else {
            this.onPageChanged.emit({ overViewChange: true, exercise: undefined, forceSave: false });
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

    savedExercisesCount(): number {
        this.syncStateVersion();
        return this.exercises().filter((exercise) => ExamParticipationService.getSubmissionForExercise(exercise)?.submitted).length;
    }

    /**
     * Calculate the exercise status (also see exam-exercise-overview-page.component.ts --> make sure the logic is consistent).
     * Pure (no side effects), so it is safe to call from template bindings under zoneless change detection.
     *
     * @param exerciseIndex index of the exercise
     * @return the sync status of the exercise (whether the corresponding submission is saved on the server or not)
     */
    getExerciseButtonStatus(exerciseIndex: number): ExerciseButtonStatus {
        this.syncStateVersion();
        // If we are in the exam timeline we do not use not synced as not synced shows
        // that the current submission is not saved which doesn't make sense in the timeline.
        if (this.examTimeLineView()) {
            return this.exerciseIndex() === exerciseIndex ? ExerciseButtonStatus.SyncedSaved : ExerciseButtonStatus.Synced;
        }

        const exercise = this.exercises()[exerciseIndex];
        const submission = ExamParticipationService.getSubmissionForExercise(exercise);
        if (!submission) {
            // in case no participation/submission yet exists -> display synced
            // this should only occur for programming exercises
            return ExerciseButtonStatus.Synced;
        }
        if (submission.submitted && submission.isSynced) {
            return ExerciseButtonStatus.SyncedSaved;
        }
        if (submission.isSynced || this.isOnlyOfflineIDE(exercise)) {
            // green save icon
            return ExerciseButtonStatus.Synced;
        } else {
            // yellow save icon except for programming exercises with only offline IDE
            return ExerciseButtonStatus.NotSynced;
        }
    }

    /**
     * Derives the save-state icon for an exercise from the same logic as getExerciseButtonStatus.
     */
    getExerciseIcon(exerciseIndex: number): IconProp {
        switch (this.getExerciseButtonStatus(exerciseIndex)) {
            case ExerciseButtonStatus.SyncedSaved:
                return facSaveSuccess;
            case ExerciseButtonStatus.NotSynced:
                return facSaveWarning;
            default:
                return this.examTimeLineView() ? facSaveSuccess : faHourglassHalf;
        }
    }

    isOnlyOfflineIDE(exercise: Exercise): boolean {
        if (exercise instanceof ProgrammingExercise) {
            const programmingExercise = exercise as ProgrammingExercise;
            return programmingExercise.allowOfflineIde === true && programmingExercise.allowOnlineEditor === false;
        }
        return false;
    }

    toggleCollapseState() {
        this.isCollapsed.update((value) => !value);
    }

    @HostListener('window:keydown.Control.m', ['$event'])
    onKeyDownControlM(event: Event) {
        event.preventDefault();
        this.toggleCollapseState();
    }
}

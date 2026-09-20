import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { faChartBar, faClipboardList, faEye, faLightbulb, faListAlt, faPencilAlt, faRedo, faRobot, faTable, faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { Exercise, ExerciseMode, ExerciseType, getExerciseUrlSegment } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseLifecycleButtonsComponent } from 'app/quiz/manage/lifecyle-buttons/quiz-exercise-lifecycle-buttons.component';
import { isQuizEditable } from 'app/quiz/shared/service/quiz-manage-util.service';
import { Course } from 'app/course/shared/entities/course.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { EntitySummary } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { ExerciseActionBarComponent } from 'app/exercise/exercise-action-bar/exercise-action-bar.component';
import { ActionItem } from 'app/exercise/exercise-action-bar/exercise-action-bar.model';
import { ExerciseVariantAiModalWizardComponent } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal-wizard.component';
import { supportsAiVariantGeneration } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal.utils';

/**
 * Builds the course-exercise `ActionItem[]` (course-scoped routes, role and feature-toggle gates, delete wiring) and
 * renders it through the shared {@link ExerciseActionBarComponent}, which owns the collapsing-row/ellipsis-menu
 * behavior. The quiz lifecycle buttons are the only always-visible content and are projected into the bar's
 * reserved-content slot.
 */
@Component({
    selector: 'jhi-exercise-actions',
    templateUrl: './exercise-actions.component.html',
    imports: [ExerciseActionBarComponent, QuizExerciseLifecycleButtonsComponent, ExerciseVariantAiModalWizardComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseActionsComponent {
    readonly exercise = input.required<Exercise>();
    readonly courseId = input.required<number>();
    readonly course = input<Course | undefined>(undefined);

    readonly exerciseUpdated = output<Exercise>();
    readonly exerciseDeleted = output<Exercise>();
    /**
     * Width (px) the actions column must reserve for this row's quiz buttons plus the ellipsis trigger; 0 for
     * non-quiz rows. The table floors the shared column at the max across its rows (see exercise-table).
     */
    readonly quizActionsMinWidth = output<number>();

    private readonly destroyRef = inject(DestroyRef);
    private readonly textExerciseService = inject(TextExerciseService);
    private readonly fileUploadExerciseService = inject(FileUploadExerciseService);
    private readonly quizExerciseService = inject(QuizExerciseService);
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly modelingExerciseService = inject(ModelingExerciseService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly eventManager = inject(EventManager);
    private readonly translateService = inject(TranslateService);
    private readonly profileService = inject(ProfileService);
    private readonly featureToggleService = inject(FeatureToggleService);

    private readonly localCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
    /**
     * Whether programming exercises are enabled server-side; defaults to active until the toggle resolves. Actions
     * are data, not markup, so this folds into `ActionItem.disabled` instead of a `jhiFeatureToggle` directive.
     */
    private readonly programmingEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.ProgrammingExercises), { initialValue: true });

    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    /** Controls the AI variant generation wizard/modal opened via the "Create Variant with AI" action. */
    protected readonly aiVariantModalVisible = signal(false);

    /** The current exercise typed as a quiz, or `undefined` for non-quiz exercises. Drives the lifecycle buttons. */
    readonly quizExercise = computed<QuizExercise | undefined>(() => {
        const ex = this.exercise();
        return ex.type === ExerciseType.QUIZ ? ex : undefined;
    });

    /** True when the lifecycle buttons component will render at least one button. Used to show/hide the separator. */
    readonly hasQuizButtons = computed<boolean>(() => {
        const quiz = this.quizExercise();
        if (!quiz) return false;
        const showVisible = quiz.status === QuizStatus.INVISIBLE && !!quiz.isAtLeastEditor && !quiz.visibleToStudents;
        const showStart =
            (quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.INVISIBLE) && quiz.quizMode === QuizMode.SYNCHRONIZED && !!quiz.isAtLeastEditor && !quiz.quizStarted;
        const showBatches = quiz.quizMode === QuizMode.BATCHED && (quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.ACTIVE);
        const showEnd =
            (quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.ACTIVE) && quiz.quizMode !== QuizMode.SYNCHRONIZED && !!quiz.isAtLeastInstructor && !quiz.quizEnded;
        return showVisible || showStart || showBatches || showEnd;
    });

    readonly deletionSummary = computed<Observable<EntitySummary>>(() => this.exerciseService.getDeletionSummary(this.exercise()));

    /**
     * Cleanup checkboxes for the delete dialog: on external CI, programming exercises offer an opt-out for deleting
     * repositories and build plans. Hidden under LocalCI, where the request omits the flags and the server decides.
     */
    readonly deleteAdditionalChecks = computed((): { [key: string]: string } => {
        if (this.exercise().type !== ExerciseType.PROGRAMMING || this.localCIEnabled) {
            return {};
        }
        return {
            deleteStudentReposBuildPlans: 'artemisApp.programmingExercise.delete.studentReposBuildPlans',
            deleteBaseReposBuildPlans: 'artemisApp.programmingExercise.delete.baseReposBuildPlans',
        };
    });

    /** Placeholders for the delete confirmation question (`{{ courseType }}` / `{{ courseTitle }}`). */
    readonly deleteTranslateValues = computed<{ [key: string]: unknown }>(() => {
        const course = this.course();
        return {
            courseTitle: course?.title,
            courseType: this.translateService.instant(course?.testCourse ? 'artemisApp.exercise.delete.testCourse' : 'artemisApp.exercise.delete.realCourse'),
        };
    });

    /**
     * Regular actions in original display order: Teams → Participations → Scores → type-specific → Create Variant
     * with AI → Edit → Delete.
     */
    readonly mainActions = computed<ActionItem[]>(() => {
        const ex = this.exercise();
        const cid = this.courseId();
        const seg = getExerciseUrlSegment(ex.type);
        const items: ActionItem[] = [];

        if (ex.mode === ExerciseMode.TEAM) {
            items.push({
                id: 'teams',
                labelKey: 'artemisApp.exercise.teams',
                icon: faUsers,
                severity: 'primary',
                kind: 'link',
                link: ['/course-management', cid, 'exercises', ex.id!, 'teams'],
            });
        }
        items.push({
            id: 'participations',
            labelKey: 'artemisApp.exercise.participations',
            icon: faListAlt,
            severity: 'primary',
            kind: 'link',
            link: ['/course-management', cid, seg, ex.id!, 'participations'],
        });
        items.push({
            id: 'scores',
            labelKey: 'entity.action.scores',
            icon: faTable,
            severity: 'info',
            kind: 'link',
            link: ['/course-management', cid, seg, ex.id!, 'scores'],
        });
        if (ex.type === ExerciseType.QUIZ) {
            const q = ex as QuizExercise;
            items.push({
                id: 'statistics',
                labelKey: 'artemisApp.quizExercise.statistics',
                icon: faChartBar,
                severity: 'info',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'quiz-point-statistic'],
            });
            items.push({
                id: 'preview',
                labelKey: 'artemisApp.quizExercise.preview',
                icon: faEye,
                severity: 'success',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'preview'],
            });
            items.push({
                id: 'solution',
                labelKey: 'artemisApp.quizExercise.solution',
                icon: faLightbulb,
                severity: 'success',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'solution'],
            });
            if (q.quizEnded && ex.isAtLeastInstructor) {
                items.push({
                    id: 're-evaluate',
                    labelKey: 'entity.action.re-evaluate',
                    icon: faRedo,
                    severity: 'warn',
                    kind: 'link',
                    link: ['/course-management', cid, seg, ex.id!, 're-evaluate'],
                });
            }
        }
        // Both example-submission routes are IS_AT_LEAST_EDITOR, so tutors would only hit an access denial.
        if (ex.isAtLeastEditor && (ex.type === ExerciseType.MODELING || ex.type === ExerciseType.TEXT)) {
            items.push({
                id: 'examples',
                labelKey: 'entity.action.exampleSubmissions',
                icon: faClipboardList,
                severity: 'success',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'example-submissions'],
            });
        }
        // Sits between the info/success-colored buttons above and the warning-colored edit buttons below, matching its
        // own warning color. Only offered for exercise types the generator supports; the server rejects the rest.
        if (ex.isAtLeastEditor && supportsAiVariantGeneration(ex)) {
            items.push({
                id: 'create-variant-ai',
                labelKey: 'artemisApp.exerciseManagement.action.createVariantWithAi',
                icon: faRobot,
                severity: 'warn',
                kind: 'button',
                onClick: () => this.aiVariantModalVisible.set(true),
            });
        }
        // Programming-only actions stay visible but go inert while the feature toggle is off.
        const programmingDisabled = ex.type === ExerciseType.PROGRAMMING && !this.programmingEnabled();
        // Editing requires editor rights, so tutors must not see the edit controls.
        if (ex.type === ExerciseType.PROGRAMMING && ex.isAtLeastEditor) {
            items.push({
                id: 'edit-in-editor',
                labelKey: 'entity.action.editInEditor',
                icon: faPencilAlt,
                severity: 'warn',
                kind: 'link',
                link: ['/course-management', cid, 'programming-exercises', ex.id!, 'code-editor', RepositoryType.TEMPLATE, -1],
                disabled: programmingDisabled || undefined,
                disabledTooltip: programmingDisabled ? 'artemisApp.exerciseManagement.programmingFeatureDisabled' : undefined,
            });
        }
        if (ex.isAtLeastEditor) {
            if (ex.type !== ExerciseType.QUIZ) {
                items.push({
                    id: 'edit',
                    labelKey: 'entity.action.edit',
                    icon: faWrench,
                    severity: 'warn',
                    kind: 'link',
                    link: ['/course-management', cid, seg, ex.id!, 'edit'],
                });
            } else {
                const q2 = ex as QuizExercise;
                // Prefer the server-supplied isEditable (set by loadQuizBatches); until it arrives, check client-side.
                const editable = q2.isEditable !== false && (q2.isEditable === true || isQuizEditable(q2));
                const editDisabled = !editable || !!q2.quizEnded;
                items.push({
                    id: 'edit',
                    labelKey: 'entity.action.edit',
                    icon: faWrench,
                    severity: 'warn',
                    kind: 'link',
                    link: ['/course-management', cid, seg, ex.id!, 'edit'],
                    disabled: editDisabled || undefined,
                    disabledTooltip: q2.quizEnded
                        ? 'artemisApp.quizExercise.edit.editNotPossibleAfterEnd'
                        : !editable && q2.status === QuizStatus.ACTIVE
                          ? 'artemisApp.quizExercise.editNotPossibleDuringQuiz'
                          : !editable
                            ? 'artemisApp.quizExercise.editNotPossibleStudentsStarted'
                            : undefined,
                });
            }
        }
        if (ex.isAtLeastInstructor) {
            items.push({
                id: 'delete',
                labelKey: 'entity.action.delete',
                icon: faTrash,
                severity: 'danger',
                kind: 'delete',
                disabled: programmingDisabled || undefined,
                disabledTooltip: programmingDisabled ? 'artemisApp.exerciseManagement.programmingFeatureDisabled' : undefined,
                delete: {
                    entityTitle: ex.title ?? '',
                    entitySummaryTitle: 'artemisApp.exercise.delete.summary.title',
                    fetchEntitySummary: this.deletionSummary(),
                    deleteQuestion: 'artemisApp.exercise.delete.question',
                    deleteConfirmationText: 'artemisApp.exercise.delete.typeNameToConfirm',
                    translateValues: this.deleteTranslateValues(),
                    additionalChecks: this.deleteAdditionalChecks(),
                    dialogError: this.dialogError$,
                    onDelete: (event) => this.onDelete(event),
                },
            });
        }
        return items;
    });

    /**
     * Relays an optimistic quiz update to the parent, recomputing the client-derived `status` / `quizStarted` flags first
     * so the action buttons reflect the new state immediately.
     */
    protected onQuizLifecycleUpdate(quiz: QuizExercise): void {
        quiz.status = this.quizExerciseService.getStatus(quiz);
        quiz.quizStarted = quiz.status === QuizStatus.ACTIVE;
        this.exerciseUpdated.emit(quiz);
    }

    /**
     * Re-fetches the quiz when the lifecycle component asks for a reload (e.g. after a failed mutation reverted the
     * optimistic state), since the stale local copy would keep offering an action the server just rejected.
     */
    protected onQuizReload(): void {
        const exerciseId = this.exercise().id;
        if (exerciseId === undefined) {
            return;
        }
        this.quizExerciseService
            .find(exerciseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (response) => {
                    const quiz = response.body;
                    if (quiz) {
                        quiz.status = this.quizExerciseService.getStatus(quiz);
                        quiz.quizStarted = quiz.status === QuizStatus.ACTIVE;
                        this.exerciseUpdated.emit(quiz);
                    }
                },
                error: (e: HttpErrorResponse) => this.dialogErrorSource.next(e.message),
            });
    }

    protected onDelete(event: { [key: string]: boolean }): void {
        const exercise = this.exercise();
        const exerciseId = exercise.id;
        if (exerciseId === undefined) {
            return;
        }
        const finish = (obs: Observable<unknown>, evtName: string) =>
            obs.subscribe({
                next: () => {
                    this.eventManager.broadcast({ name: evtName, content: 'Deleted an exercise' });
                    this.dialogErrorSource.next('');
                    // Notify the parent so the deleted exercise is removed from the view without a page refresh.
                    this.exerciseDeleted.emit(exercise);
                },
                error: (e: HttpErrorResponse) => this.dialogErrorSource.next(e.message),
            });

        switch (exercise.type) {
            case ExerciseType.TEXT:
                finish(this.textExerciseService.delete(exerciseId), 'textExerciseListModification');
                break;
            case ExerciseType.FILE_UPLOAD:
                finish(this.fileUploadExerciseService.delete(exerciseId), 'fileUploadExerciseListModification');
                break;
            case ExerciseType.QUIZ:
                finish(this.quizExerciseService.delete(exerciseId), 'quizExerciseListModification');
                break;
            case ExerciseType.MODELING:
                finish(this.modelingExerciseService.delete(exerciseId), 'modelingExerciseListModification');
                break;
            case ExerciseType.PROGRAMMING:
                finish(
                    this.programmingExerciseService.delete(exerciseId, event.deleteStudentReposBuildPlans, event.deleteBaseReposBuildPlans),
                    'programmingExerciseListModification',
                );
                break;
        }
    }
}

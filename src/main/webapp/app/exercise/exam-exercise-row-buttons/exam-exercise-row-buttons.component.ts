import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { ActionItem } from 'app/exercise/exercise-action-bar/exercise-action-bar.model';
import { ExerciseActionBarComponent } from 'app/exercise/exercise-action-bar/exercise-action-bar.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { TranslateService } from '@ngx-translate/core';
import { faBook, faExclamationTriangle, faEye, faFileSignature, faPencilAlt, faRobot, faSignal, faTable, faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiTooltipDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ExerciseVariantAiModalWizardComponent } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal-wizard.component';
import { supportsAiVariantGeneration } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal.utils';

/** setTimeout truncates delays beyond a signed 32-bit millisecond value. */
const MAX_TIMEOUT_MS = 2 ** 31 - 1;

/**
 * Builds the exam-exercise `ActionItem[]` (exam-scoped routes, exam-specific role/lifecycle gates, delete wiring) and
 * renders it through the shared {@link ExerciseActionBarComponent} — the same collapsible-row/ellipsis-menu bar the
 * course-exercise table uses, so both look and behave identically, including collapsing on small screens. The
 * test-run-participations warning is the only always-visible content, projected into the bar's reserved-content slot.
 */
@Component({
    selector: 'jhi-exam-exercise-row-buttons',
    templateUrl: './exam-exercise-row-buttons.component.html',
    imports: [ExerciseActionBarComponent, FaIconComponent, TumUiTooltipDirective, ArtemisTranslatePipe, ExerciseVariantAiModalWizardComponent],
})
export class ExamExerciseRowButtonsComponent {
    private textExerciseService = inject(TextExerciseService);
    private fileUploadExerciseService = inject(FileUploadExerciseService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private modelingExerciseService = inject(ModelingExerciseService);
    private quizExerciseService = inject(QuizExerciseService);
    private exerciseService = inject(ExerciseService);
    private eventManager = inject(EventManager);
    private profileService = inject(ProfileService);
    private translateService = inject(TranslateService);

    readonly course = input.required<Course>();
    readonly exercise = input.required<Exercise>();
    readonly exam = input.required<Exam>();
    readonly exerciseGroupId = input.required<number>();
    readonly latestIndividualEndDate = input<dayjs.Dayjs>();
    readonly onDeleteExercise = output<void>();
    /**
     * Width (px) the actions column must reserve to keep the always-visible test-run warning plus the ellipsis
     * trigger on screen; 0 when there is none. Mirrors `ExerciseActionsComponent.quizActionsMinWidth` so a shared
     * table column can floor its width the same way.
     */
    readonly actionsMinWidth = output<number>();

    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    protected readonly faExclamationTriangle = faExclamationTriangle;

    /** Controls the AI variant generation wizard opened via the "Create Variant with AI" action. */
    readonly aiVariantModalVisible = signal(false);

    private readonly localCIEnabled = signal(this.profileService.isProfileActive(PROFILE_LOCALCI));

    /**
     * The wall clock as a signal. The action set depends on the exam's start and end having passed, and nothing
     * tracks the clock, so a page left open across either boundary would keep serving a stale set of actions.
     */
    private readonly now = signal(dayjs());

    /**
     * Whether the exam is over (using the latest individual end date), gating the quiz re-evaluate action and
     * whether the quiz edit action is shown at all.
     */
    isExamOver(): boolean {
        const latestIndividualEndDate = this.latestIndividualEndDate();
        return latestIndividualEndDate ? latestIndividualEndDate.isBefore(this.now()) : false;
    }

    /** Whether the exam has started, disabling the quiz edit action (students may already be working on it). */
    hasExamStarted(): boolean {
        const exam = this.exam();
        return exam.startDate ? exam.startDate.isBefore(this.now()) : false;
    }

    constructor() {
        // Advance `now` exactly when the next boundary passes, so the actions recompute once instead of polling.
        effect((onCleanup) => {
            const next = [this.exam().startDate, this.latestIndividualEndDate()]
                .filter((date): date is dayjs.Dayjs => date !== undefined)
                .map((date) => date.valueOf())
                .filter((time) => time > this.now().valueOf())
                .sort((a, b) => a - b)
                .at(0);
            if (next === undefined) {
                return;
            }
            // setTimeout caps at a 32-bit delay; a boundary further out re-schedules when that intermediate tick fires.
            const handle = setTimeout(() => this.now.set(dayjs()), Math.min(Math.max(next - Date.now(), 0) + 1, MAX_TIMEOUT_MS));
            onCleanup(() => clearTimeout(handle));
        });
    }

    /** The always-visible test-run warning shown next to the quiz edit action, before the exam ends. */
    protected readonly showTestRunWarning = computed(() => this.exercise().type === ExerciseType.QUIZ && !!this.exercise().testRunParticipationsExist && !this.isExamOver());

    private readonly deletionSummary = computed(() => this.exerciseService.getDeletionSummary(this.exercise()));

    private readonly deleteAdditionalChecks = computed((): { [key: string]: string } => {
        if (this.exercise().type !== ExerciseType.PROGRAMMING || this.localCIEnabled()) {
            return {};
        }
        return {
            deleteStudentReposBuildPlans: 'artemisApp.programmingExercise.delete.studentReposBuildPlans',
            deleteBaseReposBuildPlans: 'artemisApp.programmingExercise.delete.baseReposBuildPlans',
        };
    });

    /** Placeholders for the delete confirmation question (`{{ courseType }}` / `{{ courseTitle }}`). */
    private readonly deleteTranslateValues = computed<{ [key: string]: unknown }>(() => {
        const course = this.course();
        return {
            courseTitle: course.title,
            // The dialog interpolates these verbatim, so the course type must already be translated here.
            courseType: this.translateService.instant(course.testCourse ? 'artemisApp.exercise.delete.testCourse' : 'artemisApp.exercise.delete.realCourse'),
        };
    });

    readonly mainActions = computed<ActionItem[]>(() => {
        const ex = this.exercise();
        const course = this.course();
        const cid = course.id!;
        const exam = this.exam();
        const groupId = this.exerciseGroupId();
        const groupSeg = ['/course-management', cid, 'exams', exam.id!, 'exercise-groups', groupId] as (string | number)[];
        const typeSeg = [...groupSeg, `${ex.type}-exercises`, ex.id!];
        const items: ActionItem[] = [];

        if (course.isAtLeastInstructor) {
            items.push({
                id: 'participations',
                labelKey: 'artemisApp.exercise.participations',
                icon: faListAlt,
                severity: 'primary',
                kind: 'link',
                link: [...typeSeg, 'participations'],
            });
            items.push({ id: 'scores', labelKey: 'entity.action.scores', icon: faTable, severity: 'info', kind: 'link', link: [...typeSeg, 'scores'] });
        }
        if (course.isAtLeastEditor && ex.type === ExerciseType.PROGRAMMING) {
            items.push({
                id: 'grading',
                labelKey: 'artemisApp.programmingExercise.configureGrading.shortTitle',
                icon: faFileSignature,
                severity: 'warn',
                kind: 'link',
                link: [...groupSeg, 'programming-exercises', ex.id!, 'grading', 'test-cases'],
            });
        }
        if (course.isAtLeastEditor && ex.type !== ExerciseType.QUIZ && ex.type !== ExerciseType.PROGRAMMING && ex.type !== ExerciseType.FILE_UPLOAD) {
            items.push({
                id: 'examples',
                labelKey: 'entity.action.exampleSubmissions',
                icon: faBook,
                severity: 'success',
                kind: 'link',
                link: [...typeSeg, 'example-submissions'],
            });
        }
        if (course.isAtLeastInstructor && ex.type === ExerciseType.QUIZ) {
            items.push({
                id: 'statistics',
                labelKey: 'artemisApp.quizExercise.statistics',
                icon: faSignal,
                severity: 'info',
                kind: 'link',
                link: [...groupSeg, 'quiz-exercises', ex.id!, 'quiz-point-statistic'],
            });
        }
        if (course.isAtLeastInstructor && ex.teamMode) {
            items.push({
                id: 'teams',
                labelKey: 'artemisApp.exercise.teams',
                icon: faUsers,
                severity: 'primary',
                kind: 'link',
                link: ['/course-management', cid, 'exercises', ex.id!, 'teams'],
            });
        }
        // Sits between the info/success-colored actions above and the warning-colored edit actions below, matching its
        // own warning color. Only offered for exercise types the generator supports; the server rejects the rest.
        if (course.isAtLeastEditor && supportsAiVariantGeneration(ex)) {
            items.push({
                id: 'create-variant-ai',
                labelKey: 'artemisApp.exerciseManagement.action.createVariantWithAi',
                icon: faRobot,
                severity: 'warn',
                kind: 'button',
                onClick: () => this.aiVariantModalVisible.set(true),
            });
        }
        if (course.isAtLeastEditor && ex.type === ExerciseType.PROGRAMMING) {
            items.push({
                id: 'edit-in-editor',
                labelKey: 'entity.action.editInEditor',
                icon: faPencilAlt,
                severity: 'warn',
                kind: 'link',
                link: ['/course-management', cid, 'programming-exercises', ex.id!, 'code-editor', RepositoryType.TEMPLATE, -1],
            });
        }
        if (course.isAtLeastEditor && ex.type === ExerciseType.QUIZ) {
            items.push({
                id: 'preview',
                labelKey: 'artemisApp.quizExercise.preview',
                icon: faEye,
                severity: 'success',
                kind: 'link',
                link: [...groupSeg, 'quiz-exercises', ex.id!, 'preview'],
            });
            items.push({
                id: 'solution',
                labelKey: 'artemisApp.quizExercise.solution',
                icon: faEye,
                severity: 'success',
                kind: 'link',
                link: [...groupSeg, 'quiz-exercises', ex.id!, 'solution'],
            });
            if (this.isExamOver() && course.isAtLeastInstructor) {
                items.push({
                    id: 're-evaluate',
                    labelKey: 'entity.action.re-evaluate',
                    icon: faWrench,
                    severity: 'warn',
                    kind: 'link',
                    link: [...groupSeg, 'quiz-exercises', ex.id!, 're-evaluate'],
                });
            }
            if (!this.isExamOver()) {
                items.push({
                    id: 'edit',
                    labelKey: 'entity.action.edit',
                    icon: faWrench,
                    severity: 'warn',
                    kind: 'link',
                    link: [...typeSeg, 'edit'],
                    disabled: this.hasExamStarted(),
                    disabledTooltip: this.hasExamStarted() ? 'artemisApp.examManagement.exerciseGroup.editNotPossibleExamStarted' : undefined,
                });
            }
        } else if (course.isAtLeastEditor) {
            items.push({ id: 'edit', labelKey: 'entity.action.edit', icon: faWrench, severity: 'warn', kind: 'link', link: [...typeSeg, 'edit'] });
        }
        if (course.isAtLeastInstructor) {
            items.push({
                id: 'delete',
                labelKey: 'entity.action.delete',
                icon: faTrash,
                severity: 'danger',
                kind: 'delete',
                delete: {
                    entityTitle: ex.title ?? '',
                    entitySummaryTitle: 'artemisApp.exercise.delete.summary.title',
                    fetchEntitySummary: this.deletionSummary(),
                    deleteQuestion: ex.type === ExerciseType.PROGRAMMING ? 'artemisApp.programmingExercise.delete.question' : 'artemisApp.exercise.delete.question',
                    deleteConfirmationText: 'artemisApp.exercise.delete.typeNameToConfirm',
                    translateValues: this.deleteTranslateValues(),
                    additionalChecks: this.deleteAdditionalChecks(),
                    dialogError: this.dialogError$,
                    onDelete: (event) => (ex.type === ExerciseType.PROGRAMMING ? this.deleteProgrammingExercise(event) : this.deleteExercise()),
                },
            });
        }
        return items;
    });

    /**
     * Deletes an exercise. ExerciseType is used to choose the right service for deletion.
     */
    deleteExercise() {
        switch (this.exercise().type) {
            case ExerciseType.TEXT:
                this.deleteTextExercise();
                break;
            case ExerciseType.FILE_UPLOAD:
                this.deleteFileUploadExercise();
                break;
            case ExerciseType.QUIZ:
                this.deleteQuizExercise();
                break;
            case ExerciseType.MODELING:
                this.deleteModelingExercise();
                break;
        }
    }

    private deleteTextExercise() {
        this.textExerciseService.delete(this.exercise().id!).subscribe({
            next: () => {
                this.eventManager.broadcast({ name: 'textExerciseListModification', content: 'Deleted a textExercise' });
                this.dialogErrorSource.next('');
                this.onDeleteExercise.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    private deleteModelingExercise() {
        this.modelingExerciseService.delete(this.exercise().id!).subscribe({
            next: () => {
                this.eventManager.broadcast({ name: 'modelingExerciseListModification', content: 'Deleted a modelingExercise' });
                this.dialogErrorSource.next('');
                this.onDeleteExercise.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    private deleteFileUploadExercise() {
        this.fileUploadExerciseService.delete(this.exercise().id!).subscribe({
            next: () => {
                this.eventManager.broadcast({ name: 'fileUploadExerciseListModification', content: 'Deleted a fileUploadExercise' });
                this.dialogErrorSource.next('');
                this.onDeleteExercise.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    private deleteQuizExercise() {
        this.quizExerciseService.delete(this.exercise().id!).subscribe({
            next: () => {
                this.eventManager.broadcast({ name: 'quizExerciseListModification', content: 'Deleted a quiz' });
                this.dialogErrorSource.next('');
                this.onDeleteExercise.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    public deleteProgrammingExercise(event: { [key: string]: boolean }) {
        this.programmingExerciseService.delete(this.exercise().id!, event.deleteStudentReposBuildPlans, event.deleteBaseReposBuildPlans).subscribe({
            next: () => {
                this.eventManager.broadcast({ name: 'programmingExerciseListModification', content: 'Deleted a programming exercise' });
                this.dialogErrorSource.next('');
                this.onDeleteExercise.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Exports questions for the given quiz exercise in json file
     * @param exportAll If true exports all questions, else exports only those whose export flag is true
     */
    exportQuizById(exportAll: boolean) {
        this.quizExerciseService.find(this.exercise().id!).subscribe((res: HttpResponse<QuizExercise>) => {
            const exercise = res.body!;
            this.quizExerciseService.exportQuiz(exercise.quizQuestions, exportAll, exercise.title);
        });
    }
}

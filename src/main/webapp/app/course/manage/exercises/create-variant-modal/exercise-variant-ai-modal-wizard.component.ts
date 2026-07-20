import { Component, OnDestroy, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import dayjs from 'dayjs/esm';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import {
    faArrowLeft,
    faArrowRight,
    faBan,
    faBookOpen,
    faCheck,
    faChevronDown,
    faChevronRight,
    faCircleCheck,
    faEarthAmericas,
    faGaugeHigh,
    faGears,
    faInfoCircle,
    faLayerGroup,
    faPenToSquare,
    faRobot,
    faTriangleExclamation,
    faWandMagicSparkles,
} from '@fortawesome/free-solid-svg-icons';
import { Subscription } from 'rxjs';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { RadioButtonModule } from 'primeng/radiobutton';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DifficultyLevel, Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseVariantGroupDTO, ExerciseVariantGroupService } from 'app/course/manage/exercises/exercise-variant-group.service';
import { QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ExerciseVariantGenerationService } from 'app/hyperion/services/exercise-variant-generation.service';
import { VariantGenerationEvent, VariantJobPhase, isTerminalVariantPhase } from 'app/hyperion/services/exercise-variant-websocket.service';
import { VariantGenerationRequest, VariantGenerationRequestNarrativeStyleEnum } from 'app/openapi/model/variant-generation-request';
import { VariantPlacement } from 'app/openapi/model/variant-placement';
import { StepOutput } from 'app/openapi/model/step-output';
import { PlacementChoice, adaptationChips, difficultyBadgeClass, difficultyLabel, durationDays } from './exercise-variant-ai-modal.utils';

type WizardStep = 1 | 2 | 3 | 4 | 5;

/**
 * The wizard's step indicator. `step` is the WizardStep the entry represents — carried explicitly rather than
 * derived from the array index, because the Placement step is dropped for exam exercises (see `wizardSteps`)
 * while the remaining steps keep their numbers.
 */
const WIZARD_STEPS: ReadonlyArray<{ step: WizardStep; label: string; icon: IconDefinition }> = [
    { step: 1, label: 'Select', icon: faWandMagicSparkles },
    { step: 2, label: 'Configure', icon: faGears },
    { step: 3, label: 'Placement', icon: faLayerGroup },
    { step: 4, label: 'Generating', icon: faRobot },
    { step: 5, label: 'Result', icon: faCircleCheck },
];

/**
 * The storytelling options offered in the Configure step — a strength scale from plain technical wording to
 * fully creative storytelling, in ascending order. The tooltip carries the description; not selecting the
 * Storytelling card at all means "stay consistent with the source exercise's narrative" (server-side default).
 */
const NARRATIVE_STYLES: ReadonlyArray<{ value: VariantGenerationRequestNarrativeStyleEnum; label: string; description: string }> = [
    { value: 'TECHNICAL', label: 'Technical', description: 'No story — a plain, concise focus on the technical concepts.' },
    { value: 'REALISTIC', label: 'Realistic', description: 'A short real-world scenario introduces the task — the rest stays technical.' },
    { value: 'CREATIVE', label: 'Creative', description: 'A story carries the task — themed setting, named actors, story-driven examples.' },
    { value: 'IMAGINATIVE', label: 'Imaginative', description: 'Fully creative storytelling — the entire exercise is told inside a rich narrative world.' },
];

/**
 * Running pipeline phases in execution order — the wizard's step timeline is derived from VariantJobPhase
 * (single source of truth via the OpenAPI client). REPAIRING is NOT a linear step: it renders as a repeat
 * visit on the VERIFYING step with the attempt counter.
 */
const GENERATION_PHASES: readonly VariantJobPhase[] = ['ANALYZING', 'PLANNING', 'PROVISIONING', 'TRANSFORMING', 'VERIFYING', 'FINALIZING'];

@Component({
    selector: 'jhi-exercise-variant-ai-modal-wizard',
    templateUrl: './exercise-variant-ai-modal-wizard.component.html',
    styleUrl: './exercise-variant-ai-modal-wizard.component.scss',
    providers: [ConfirmationService],
    imports: [
        DialogModule,
        ButtonModule,
        RadioButtonModule,
        InputTextModule,
        TextareaModule,
        ConfirmDialogModule,
        TooltipModule,
        FormsModule,
        FaIconComponent,
        ArtemisTranslatePipe,
        NgTemplateOutlet,
    ],
})
export class ExerciseVariantAiModalWizardComponent implements OnDestroy {
    private readonly variantGenerationService = inject(ExerciseVariantGenerationService);
    private readonly variantGroupService = inject(ExerciseVariantGroupService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly confirmationService = inject(ConfirmationService);
    private readonly translateService = inject(TranslateService);

    readonly visible = input<boolean>(false);
    /** Required for the wizard flow (steps 1–3); may be absent in monitor mode (tray host has no exercise). */
    readonly sourceExercise = input<Exercise | undefined>(undefined);
    readonly courseId = input<number | undefined>(undefined);
    /**
     * Set by exam hosts (the exam exercise-group management row): the source is an exam exercise, so placement is
     * forced to the source's exam exercise group (SAME_EXAM_GROUP, no placement step). All adaptation options,
     * including difficulty, stay available — e.g. an instructor importing an old exam may deliberately generate a
     * harder variant of a too-easy exercise and delete the easy one afterwards.
     * A populated `sourceExercise().exerciseGroup` also flags this, but exam rows pass only the group id, not the
     * nested group object, so the explicit input is the reliable signal.
     */
    readonly examExercise = input<boolean>(false);
    /** Monitor mode: the tray reopens this modal for a running/finished job — skips steps 1–3. */
    readonly monitorJobId = input<string | undefined>(undefined);

    readonly visibleChange = output<boolean>();
    readonly variantAdded = output<Exercise>();

    /** Monitor mode skips the wizard chrome: no 5-step indicator, job-centric dialog title. */
    readonly isMonitorMode = computed(() => !!this.monitorJobId());
    readonly headerKey = computed(() => (this.isMonitorMode() ? 'artemisApp.exerciseVariantGeneration.wizard.monitorTitle' : 'artemisApp.exerciseVariantGeneration.wizard.title'));

    readonly wizardStep = signal<WizardStep>(1);
    readonly placementChoice = signal<PlacementChoice>('existing-group');

    readonly newGroupTitle = signal('');
    readonly newGroupMaxPoints = signal<number | undefined>(undefined);
    readonly newGroupReleaseDate = signal('');
    readonly newGroupStartDate = signal('');
    readonly newGroupDueDate = signal('');
    readonly newGroupAssessmentDueDate = signal('');

    readonly changeDifficulty = signal(false);
    readonly targetDifficulty = signal<DifficultyLevel>(DifficultyLevel.MEDIUM);
    readonly changeDomain = signal(false);
    readonly domainText = signal('');
    readonly changeNarrative = signal(false);
    readonly narrativeStyle = signal<VariantGenerationRequestNarrativeStyleEnum>('TECHNICAL');
    readonly changeCustom = signal(false);
    readonly additionalInstructions = signal('');

    // ── Live job state ────────────────────────────────────────────────────────────────────────
    readonly jobId = signal<string | undefined>(undefined);
    readonly jobPhase = signal<VariantJobPhase>('ANALYZING');
    readonly attempt = signal<number | undefined>(undefined);
    readonly maxAttempts = signal<number | undefined>(undefined);
    /** Latest PROGRESS sub-label, e.g. "Building solution repository" — cleared on phase change. */
    readonly progressDetail = signal<string | undefined>(undefined);
    /**
     * Recorded step-output history per phase, oldest first — live via STEP_OUTPUT events, full detail from the
     * job-detail endpoint. Every message is kept (one per verify/repair attempt), so an instructor can still
     * inspect the earlier error messages after a retry succeeded.
     */
    readonly stepOutputs = signal<Record<string, StepOutput[]>>({});
    readonly expandedPhases = signal<Record<string, boolean>>({});
    readonly warnings = signal<string[]>([]);
    readonly failurePhase = signal<VariantJobPhase | undefined>(undefined);
    readonly failureDetail = signal<string | undefined>(undefined);
    /** AI-generated state-and-next-steps summary for failed jobs (server-side best effort, may stay empty). */
    readonly instructorSummary = signal<string | undefined>(undefined);
    /** Job-record context for monitor mode, where no sourceExercise input exists (drives the flow card). */
    readonly monitorSourceTitle = signal<string | undefined>(undefined);
    readonly monitorExerciseType = signal<ExerciseType | undefined>(undefined);
    /** The planned variant title — available from PLANNING on, drives the "source → variant" display. */
    readonly variantTitle = signal<string | undefined>(undefined);
    /** The original generation request, fetched with the job detail (monitor mode / live refresh). */
    readonly monitorRequest = signal<VariantGenerationRequest | undefined>(undefined);
    /** Accumulated LLM tokens (planning + agent rounds + critique) — telemetry chip on the result view. */
    readonly totalTokensUsed = signal<number | undefined>(undefined);
    readonly generatedVariant = signal<Exercise | undefined>(undefined);

    /**
     * The variant group the source exercise already belongs to, loaded from the course's variant groups on
     * open — offers the "add to existing group" placement.
     */
    readonly sourceGroup = signal<ExerciseVariantGroupDTO | undefined>(undefined);

    /** Exam exercises skip the placement step entirely — SAME_EXAM_GROUP is forced. */
    readonly isExamExercise = computed(() => this.examExercise() || !!this.sourceExercise()?.exerciseGroup);

    /**
     * Whether the source exercise itself can join a freshly created group — that is the whole promise of the
     * NEW_GROUP placement ("group the variant WITH its source"). Mirrors the server's rules in
     * `VariantPlacementService.assignSourceToNewGroup` / `ExerciseVariantGroupService.assignExerciseToGroup`,
     * which skip the source with a warning when it cannot legally be a member. Offering the option for those
     * sources would silently produce a group holding only the variant, so it is hidden instead.
     */
    readonly canGroupSourceWithVariant = computed(() => {
        const source = this.sourceExercise();
        if (!source || this.isExamExercise()) {
            return false;
        }
        // Already grouped: the server keeps the source in its current group, so use "add to existing group".
        if (this.sourceGroup()) {
            return false;
        }
        // Synchronized/batched quizzes have a single shared run and cannot share a group timeline.
        if (source.type === ExerciseType.QUIZ) {
            return (source as QuizExercise).quizMode === QuizMode.INDIVIDUAL;
        }
        return true;
    });

    readonly availableDifficulties = computed<Array<{ value: DifficultyLevel; label: string }>>(() => {
        const current = this.sourceExercise()?.difficulty;
        return ([DifficultyLevel.EASY, DifficultyLevel.MEDIUM, DifficultyLevel.HARD] as DifficultyLevel[])
            .filter((d) => d !== current)
            .map((d) => ({ value: d, label: difficultyLabel(d) }));
    });

    readonly anyCardSelected = computed(() => this.changeDifficulty() || this.changeDomain() || this.changeNarrative() || this.changeCustom());

    readonly canProceedToPlacement = computed(() => {
        if (!this.anyCardSelected()) return false;
        // Custom instructions are the only selection that needs non-empty configuration to mean anything.
        if (this.changeCustom() && this.additionalInstructions().trim().length === 0 && !this.changeDifficulty() && !this.changeDomain() && !this.changeNarrative()) {
            return false;
        }
        return true;
    });

    readonly canGenerate = computed(() => this.placementChoice() !== 'new-group' || this.newGroupTitle().trim().length > 0);

    /** Index of the active timeline step; REPAIRING maps back onto the VERIFYING step (repeat visit). */
    readonly currentPhaseIndex = computed(() => {
        const phase = this.jobPhase();
        if (isTerminalVariantPhase(phase)) {
            return GENERATION_PHASES.length;
        }
        const effective = phase === 'REPAIRING' ? 'VERIFYING' : phase;
        return Math.max(0, GENERATION_PHASES.indexOf(effective));
    });

    readonly isRunning = computed(() => this.jobId() !== undefined && !isTerminalVariantPhase(this.jobPhase()));

    /** "source → variant" flow card: wizard mode uses the input exercise, monitor mode the job record. */
    readonly displaySourceTitle = computed(() => this.sourceExercise()?.title ?? this.monitorSourceTitle());
    readonly displayExerciseType = computed(() => this.sourceExercise()?.type ?? this.monitorExerciseType());
    readonly displayVariantTitle = computed(() => this.generatedVariant()?.title ?? this.variantTitle());

    /** "What is being adapted" chips: the fetched request wins; a fresh wizard run uses the form state. */
    readonly adaptations = computed<string[]>(() => {
        const request = this.monitorRequest();
        if (request) {
            return adaptationChips(request);
        }
        return adaptationChips({
            targetDifficulty: this.changeDifficulty() ? this.targetDifficulty() : undefined,
            domainText: this.changeDomain() ? this.domainText().trim() : undefined,
            narrativeStyle: this.changeNarrative() ? this.narrativeStyle() : undefined,
            additionalInstructions: this.changeCustom() ? this.additionalInstructions().trim() : undefined,
        });
    });

    /**
     * Recorded step-output histories in pipeline order — the result step's "what the AI did" summary;
     * failed/warning summaries explain what was applied and where the instructor should continue.
     */
    readonly recordedStepOutputs = computed<Array<{ phase: string; outputs: StepOutput[] }>>(() => {
        const outputs = this.stepOutputs();
        return [...GENERATION_PHASES, 'REPAIRING'].filter((phase) => outputs[phase]?.length).map((phase) => ({ phase, outputs: outputs[phase] }));
    });

    readonly generationPhases = GENERATION_PHASES;
    readonly narrativeStyles = NARRATIVE_STYLES;

    /**
     * The steps the indicator shows. Exam variants are always placed in the source's exam exercise group, so the
     * wizard never visits Placement (see `goToPlacement`) — advertising a step that is silently skipped reads as
     * "you will be asked where to place this", which is exactly what exam mode must not do.
     */
    readonly wizardSteps = computed(() => WIZARD_STEPS.filter((step) => !(step.step === 3 && this.isExamExercise())));

    protected readonly faRobot = faRobot;
    protected readonly faInfoCircle = faInfoCircle;
    protected readonly faWandMagicSparkles = faWandMagicSparkles;
    protected readonly faCheck = faCheck;
    protected readonly faBan = faBan;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronRight = faChevronRight;
    protected readonly faGaugeHigh = faGaugeHigh;
    protected readonly faEarthAmericas = faEarthAmericas;
    protected readonly faBookOpen = faBookOpen;
    protected readonly faPenToSquare = faPenToSquare;
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faArrowRight = faArrowRight;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly DifficultyLevel = DifficultyLevel;
    protected readonly ExerciseType = ExerciseType;
    protected readonly getIcon = getIcon;
    protected readonly durationDays = durationDays;
    protected readonly difficultyBadgeClass = difficultyBadgeClass;

    private eventsSubscription?: Subscription;

    constructor() {
        // On open: monitor mode initializes from the job-detail endpoint. A regular open always starts a
        // FRESH wizard — running jobs are monitored via the navbar tray, and several variants of the same
        // exercise may be generated simultaneously, so the wizard never auto-attaches to an existing job.
        effect(() => {
            if (!this.visible()) {
                return;
            }
            const monitorId = this.monitorJobId();
            untracked(() => {
                if (monitorId) {
                    this.openInMonitorMode(monitorId);
                } else {
                    this.loadSourceGroup();
                }
            });
        });
    }

    ngOnDestroy(): void {
        this.eventsSubscription?.unsubscribe();
    }

    /**
     * Closing the dialog while generation is running must not cancel it (close ≠ cancel): the job continues
     * server-side, the tray keeps tracking it, and reopening the modal re-attaches via the job-detail endpoint.
     * Explicit cancellation is a separate action behind a confirmation.
     */
    onClose(visible: boolean): void {
        if (visible) return;
        if (this.wizardStep() === 4 && this.isRunning()) {
            this.visibleChange.emit(false);
            return;
        }
        this.close();
    }

    toggleDifficulty(): void {
        this.changeDifficulty.set(!this.changeDifficulty());
    }

    toggleDomain(): void {
        this.changeDomain.set(!this.changeDomain());
    }

    toggleNarrative(): void {
        this.changeNarrative.set(!this.changeNarrative());
    }

    toggleCustom(): void {
        this.changeCustom.set(!this.changeCustom());
    }

    goToStep2(): void {
        this.wizardStep.set(2);
    }

    goToStep1(): void {
        this.wizardStep.set(1);
    }

    /**
     * Advance from Configure to Placement, pre-filling the new-group fields from the source exercise.
     * Exam exercises skip the placement step and start generating immediately (SAME_EXAM_GROUP forced).
     */
    goToPlacement(): void {
        if (this.isExamExercise()) {
            this.startGeneration();
            return;
        }
        const src = this.sourceExercise()!;
        this.newGroupTitle.set((src.title ?? 'Exercise').split(':')[0].trim());
        this.newGroupMaxPoints.set(src.maxPoints);
        this.newGroupReleaseDate.set(this.fmtDate(src.releaseDate));
        this.newGroupStartDate.set(this.fmtDate(src.startDate));
        this.newGroupDueDate.set(this.fmtDate(src.dueDate));
        this.newGroupAssessmentDueDate.set(this.fmtDate(src.assessmentDueDate));
        // Never preselect a hidden option: a source that cannot join a new group falls back to standalone.
        this.placementChoice.set(this.sourceGroup() ? 'existing-group' : this.canGroupSourceWithVariant() ? 'new-group' : 'standalone');
        this.wizardStep.set(3);
    }

    close(): void {
        // Deliberately does NOT cancel a running job and does not detach the tray — only this modal's local
        // event subscription is released; the service keeps its own subscription for the tray state.
        this.eventsSubscription?.unsubscribe();
        this.eventsSubscription = undefined;
        this.wizardStep.set(1);
        this.resetJobState();
        this.sourceGroup.set(undefined);
        this.placementChoice.set('existing-group');
        this.newGroupTitle.set('');
        this.newGroupMaxPoints.set(undefined);
        this.newGroupReleaseDate.set('');
        this.newGroupStartDate.set('');
        this.newGroupDueDate.set('');
        this.newGroupAssessmentDueDate.set('');
        this.changeDifficulty.set(false);
        this.changeDomain.set(false);
        this.domainText.set('');
        this.changeNarrative.set(false);
        this.narrativeStyle.set('TECHNICAL');
        this.changeCustom.set(false);
        this.additionalInstructions.set('');
        this.visibleChange.emit(false);
    }

    /** Starts the real backend job — intents by field presence, no title input. */
    startGeneration(): void {
        const sourceExercise = this.sourceExercise();
        if (!sourceExercise?.id) return;
        const request: VariantGenerationRequest = {
            targetDifficulty: this.changeDifficulty() ? this.targetDifficulty() : undefined,
            domainText: this.changeDomain() && this.domainText().trim() ? this.domainText().trim() : undefined,
            narrativeStyle: this.changeNarrative() ? this.narrativeStyle() : undefined,
            additionalInstructions: this.changeCustom() && this.additionalInstructions().trim() ? this.additionalInstructions().trim() : undefined,
            placement: this.buildPlacement(),
        };
        this.resetJobState();
        this.wizardStep.set(4);
        this.variantGenerationService.startGeneration(sourceExercise.id, request, sourceExercise.title).subscribe({
            next: (jobId) => this.attachToJob(jobId),
            error: () => {
                this.failurePhase.set('ANALYZING');
                this.failureDetail.set(this.translateService.instant('artemisApp.exerciseVariantGeneration.wizard.startFailed'));
                this.jobPhase.set('FAILED');
                this.wizardStep.set(5);
            },
        });
    }

    /** Explicit cooperative cancel while running — distinct from closing the dialog. */
    cancelGeneration(event: Event): void {
        const jobId = this.jobId();
        if (!jobId) return;
        this.confirmationService.confirm({
            target: event.target as EventTarget,
            message: this.translateService.instant('artemisApp.exerciseVariantGeneration.tray.cancelConfirmation'),
            accept: () => this.variantGenerationService.cancelJob(jobId).subscribe({ error: () => {} }),
        });
    }

    /** Emits the REAL fetched exercise — bound in exercise-actions.component.html. */
    confirmVariant(): void {
        const variant = this.generatedVariant();
        if (!variant) return;

        this.variantAdded.emit(variant);
        this.close();
    }

    /** A phase's panel is expandable once at least one message carries a full log detail. */
    hasAnyDetail(outputs: StepOutput[]): boolean {
        return outputs.some((output) => !!output.detail);
    }

    toggleStepOutput(phase: string): void {
        this.expandedPhases.update((expanded) => {
            const updated = Object.assign({}, expanded);
            updated[phase] = !updated[phase];
            return updated;
        });
    }

    /** REPAIRING is a repeat visit on the VERIFYING step — swap that step's label while repairing. */
    stepLabelKey(phase: VariantJobPhase): string {
        const effective = phase === 'VERIFYING' && this.jobPhase() === 'REPAIRING' ? 'REPAIRING' : phase;
        return `artemisApp.exerciseVariantGeneration.phase.${effective}`;
    }

    /** Sub-label under the active step: repair attempts win over plain progress details. */
    activeStepDetail(): string | undefined {
        if (this.jobPhase() === 'REPAIRING' && this.attempt() !== undefined) {
            const attemptLabel = this.translateService.instant('artemisApp.exerciseVariantGeneration.tray.attempt', {
                attempt: this.attempt(),
                max: this.maxAttempts(),
            });
            return this.progressDetail() ? `${this.progressDetail()} — ${attemptLabel}` : attemptLabel;
        }
        return this.progressDetail();
    }

    private buildPlacement(): VariantPlacement {
        if (this.isExamExercise()) {
            return { type: 'SAME_EXAM_GROUP' };
        }
        switch (this.placementChoice()) {
            case 'existing-group':
                return this.sourceGroup() ? { type: 'EXISTING_GROUP', existingGroupId: this.sourceGroup()!.id } : { type: 'STANDALONE' };
            case 'new-group':
                return {
                    type: 'NEW_GROUP',
                    newGroup: {
                        title: this.newGroupTitle().trim(),
                        maxPoints: this.newGroupMaxPoints(),
                        releaseDate: this.toIso(this.newGroupReleaseDate()),
                        startDate: this.toIso(this.newGroupStartDate()),
                        dueDate: this.toIso(this.newGroupDueDate()),
                        assessmentDueDate: this.toIso(this.newGroupAssessmentDueDate()),
                    },
                };
            default:
                return { type: 'STANDALONE' };
        }
    }

    private attachToJob(jobId: string): void {
        this.jobId.set(jobId);
        this.eventsSubscription?.unsubscribe();
        this.eventsSubscription = this.variantGenerationService.jobEvents(jobId).subscribe((event) => this.handleEvent(event));
    }

    private handleEvent(event: VariantGenerationEvent): void {
        switch (event.type) {
            case 'PHASE_CHANGED':
                if (event.phase && event.phase !== this.jobPhase()) {
                    this.jobPhase.set(event.phase);
                    this.progressDetail.set(undefined);
                    if (event.phase !== 'REPAIRING' && event.phase !== 'VERIFYING') {
                        this.attempt.set(undefined);
                        this.maxAttempts.set(undefined);
                    }
                }
                break;
            case 'ATTEMPT':
                this.attempt.set(event.attempt);
                this.maxAttempts.set(event.maxAttempts);
                this.progressDetail.set(event.detail ?? this.progressDetail());
                break;
            case 'PROGRESS':
                this.progressDetail.set(event.detail);
                break;
            case 'STEP_OUTPUT':
                if (event.phase && event.detail) {
                    this.recordStepOutput(event.phase, { summary: event.detail });
                }
                // The event only carries the summary; the full log detail lives on the job record. Pull it right
                // away — otherwise the step renders an expandable arrow with nothing behind it.
                this.loadFullStepOutputs();
                break;
            case 'DONE':
                this.jobPhase.set(event.phase ?? 'COMPLETED');
                this.warnings.set(event.warnings ?? []);
                this.showResult(event.variantExerciseId);
                this.loadFullStepOutputs();
                break;
            case 'FAILED':
                // The FAILED event's phase is FAILED itself — the phase the job died in is the last live phase.
                this.failurePhase.set(this.jobPhase());
                this.failureDetail.set(event.detail);
                this.jobPhase.set('FAILED');
                this.wizardStep.set(5);
                // The event carries neither the kept clone id nor full step logs — pull the job detail so
                // the failure summary can link into the editor and show complete logs.
                this.loadFullStepOutputs();
                break;
            case 'CANCELLED':
                this.jobPhase.set('CANCELLED');
                this.wizardStep.set(5);
                break;
        }
    }

    /**
     * Live STEP_OUTPUT events only carry summaries; the job record additionally holds the full detail logs
     * and — for failures — the id of a clone kept from PROVISIONING. Fetched once on a terminal event.
     */
    private loadFullStepOutputs(): void {
        const jobId = this.jobId();
        if (!jobId) {
            return;
        }
        this.variantGenerationService.getJobDetail(jobId).subscribe({
            next: (detail) => {
                this.stepOutputs.set(detail.stepOutputs ?? {});
                this.instructorSummary.set(detail.job?.instructorSummary);
                this.variantTitle.set(detail.job?.variantExerciseTitle ?? this.variantTitle());
                this.monitorRequest.set(detail.request ?? this.monitorRequest());
                this.totalTokensUsed.set(detail.job?.totalTokensUsed ?? this.totalTokensUsed());
                if (this.jobPhase() === 'FAILED') {
                    this.showResult(detail.job?.variantExerciseId);
                }
            },
            error: () => {},
        });
    }

    private showResult(variantExerciseId: number | undefined): void {
        this.wizardStep.set(5);
        if (variantExerciseId) {
            this.exerciseService.find(variantExerciseId).subscribe({
                next: (response) => this.generatedVariant.set(response.body ?? undefined),
                error: () => {},
            });
        }
    }

    /** Resolves the variant group the source exercise is a member of — basis of the "existing group" placement. */
    private loadSourceGroup(): void {
        this.sourceGroup.set(undefined);
        const courseId = this.courseId();
        const exerciseId = this.sourceExercise()?.id;
        if (!courseId || !exerciseId || this.isExamExercise()) {
            return;
        }
        this.variantGroupService.getGroupsForCourse(courseId).subscribe({
            next: (groups) => this.sourceGroup.set(groups.find((group) => group.exerciseIds?.includes(exerciseId))),
            error: () => {},
        });
    }

    /** Tray-triggered monitor mode: initialize from the job-detail endpoint and skip steps 1–3. */
    private openInMonitorMode(jobId: string): void {
        this.variantGenerationService.getJobDetail(jobId).subscribe({
            next: (detail) => {
                const job = detail.job;
                this.initializeFromJobId(jobId, job?.phase, job?.attempt, job?.maxAttempts);
                this.stepOutputs.set(detail.stepOutputs ?? {});
                this.warnings.set(job?.warnings ?? []);
                this.monitorSourceTitle.set(job?.sourceExerciseTitle);
                this.monitorExerciseType.set(job?.exerciseType as ExerciseType | undefined);
                this.variantTitle.set(job?.variantExerciseTitle);
                this.monitorRequest.set(detail.request);
                this.totalTokensUsed.set(job?.totalTokensUsed);
                if (isTerminalVariantPhase(job?.phase)) {
                    this.jobId.set(jobId);
                    this.jobPhase.set(job!.phase!);
                    this.failurePhase.set(job?.failedInPhase);
                    this.failureDetail.set(job?.failureDetail);
                    this.instructorSummary.set(job?.instructorSummary);
                    this.showResult(job?.variantExerciseId);
                }
            },
            error: () => {},
        });
    }

    private initializeFromJobId(jobId: string, phase: VariantJobPhase | undefined, attempt: number | undefined, maxAttempts: number | undefined): void {
        this.resetJobState();
        this.jobPhase.set(phase ?? 'ANALYZING');
        this.attempt.set(attempt);
        this.maxAttempts.set(maxAttempts);
        this.wizardStep.set(4);
        if (!isTerminalVariantPhase(phase)) {
            this.attachToJob(jobId);
        }
    }

    /** Appends a live event's output to the phase's history; the job-detail fetch replaces it with the full record. */
    private recordStepOutput(phase: string, output: StepOutput): void {
        this.stepOutputs.update((outputs) => {
            const updated = Object.assign({}, outputs);
            updated[phase] = [...(updated[phase] ?? []), output];
            return updated;
        });
    }

    private resetJobState(): void {
        this.jobId.set(undefined);
        this.jobPhase.set('ANALYZING');
        this.attempt.set(undefined);
        this.maxAttempts.set(undefined);
        this.progressDetail.set(undefined);
        this.stepOutputs.set({});
        this.expandedPhases.set({});
        this.warnings.set([]);
        this.failurePhase.set(undefined);
        this.failureDetail.set(undefined);
        this.instructorSummary.set(undefined);
        this.monitorSourceTitle.set(undefined);
        this.monitorExerciseType.set(undefined);
        this.variantTitle.set(undefined);
        this.monitorRequest.set(undefined);
        this.totalTokensUsed.set(undefined);
        this.generatedVariant.set(undefined);
    }

    private fmtDate(d: dayjs.Dayjs | undefined): string {
        return d?.format('YYYY-MM-DDTHH:mm') ?? '';
    }

    private toIso(value: string): string | undefined {
        return value ? dayjs(value).toISOString() : undefined;
    }
}

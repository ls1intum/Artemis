import { Component, OnDestroy, computed, input, output, signal } from '@angular/core';
import dayjs from 'dayjs/esm';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    faArrowLeft,
    faArrowRight,
    faCheck,
    faCircleCheck,
    faEarthAmericas,
    faGaugeHigh,
    faGears,
    faLayerGroup,
    faPenToSquare,
    faRobot,
    faTriangleExclamation,
    faWandMagicSparkles,
} from '@fortawesome/free-solid-svg-icons';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { RadioButtonModule } from 'primeng/radiobutton';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DifficultyLevel, Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { GENERATION_STEPS, PlacementChoice, STEP_INTERVAL_MS, difficultyBadgeClass, difficultyLabel, durationDays, generateVariant } from './exercise-variant-ai-modal.utils';

type WizardStep = 1 | 2 | 3 | 4 | 5;

const WIZARD_STEPS = [
    { label: 'Select', icon: faWandMagicSparkles },
    { label: 'Configure', icon: faGears },
    { label: 'Placement', icon: faLayerGroup },
    { label: 'Generating', icon: faRobot },
    { label: 'Result', icon: faCircleCheck },
];

@Component({
    selector: 'jhi-exercise-variant-ai-modal-wizard',
    templateUrl: './exercise-variant-ai-modal-wizard.component.html',
    styleUrl: './exercise-variant-ai-modal-wizard.component.scss',
    imports: [DialogModule, ButtonModule, RadioButtonModule, InputTextModule, TextareaModule, FormsModule, FaIconComponent, SlicePipe],
})
export class ExerciseVariantAiModalWizardComponent implements OnDestroy {
    readonly visible = input<boolean>(false);
    readonly sourceExercise = input.required<Exercise>();
    readonly courseId = input<number | undefined>(undefined);

    readonly visibleChange = output<boolean>();
    readonly variantAdded = output<Exercise>();

    readonly wizardStep = signal<WizardStep>(1);
    readonly generationStep = signal(0);
    readonly generatedVariant = signal<ProgrammingExercise | null>(null);
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
    readonly changeCustom = signal(false);
    readonly additionalInstructions = signal('');

    /**
     * TODO: wire this up once the backend/course context is available to this component; until then the
     * "existing group" placement option never applies and the wizard defaults to creating a new group.
     */
    readonly sourceGroup = computed<CourseExerciseGroup | undefined>(() => undefined);

    readonly availableDifficulties = computed<Array<{ value: DifficultyLevel; label: string }>>(() => {
        const current = this.sourceExercise().difficulty;
        return ([DifficultyLevel.EASY, DifficultyLevel.MEDIUM, DifficultyLevel.HARD] as DifficultyLevel[])
            .filter((d) => d !== current)
            .map((d) => ({ value: d, label: difficultyLabel(d) }));
    });

    readonly anyCardSelected = computed(() => this.changeDifficulty() || this.changeDomain() || this.changeCustom());

    readonly canProceedToPlacement = computed(() => {
        if (!this.anyCardSelected()) return false;
        if (this.changeCustom() && this.additionalInstructions().trim().length === 0 && !this.changeDifficulty() && !this.changeDomain()) return false;
        return true;
    });

    readonly canGenerate = computed(() => this.placementChoice() !== 'new-group' || this.newGroupTitle().trim().length > 0);

    readonly generationSteps = GENERATION_STEPS;
    readonly wizardSteps = WIZARD_STEPS;

    protected readonly faRobot = faRobot;
    protected readonly faWandMagicSparkles = faWandMagicSparkles;
    protected readonly faCheck = faCheck;
    protected readonly faGaugeHigh = faGaugeHigh;
    protected readonly faEarthAmericas = faEarthAmericas;
    protected readonly faPenToSquare = faPenToSquare;
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faArrowRight = faArrowRight;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly DifficultyLevel = DifficultyLevel;
    protected readonly ExerciseType = ExerciseType;
    protected readonly getIcon = getIcon;
    protected readonly durationDays = durationDays;
    protected readonly difficultyBadgeClass = difficultyBadgeClass;

    private generationInterval: ReturnType<typeof setInterval> | null = null;

    ngOnDestroy(): void {
        this.clearTimers();
    }

    /**
     * Closing the dialog while generation is running (step 4) must not cancel it: the component instance stays alive
     * (it is never removed from the DOM, only the p-dialog is hidden), so the interval keeps advancing in the
     * background and the user sees the up-to-date step when they reopen the modal.
     */
    onClose(visible: boolean): void {
        if (visible) return;
        if (this.wizardStep() === 4) {
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

    toggleCustom(): void {
        this.changeCustom.set(!this.changeCustom());
    }

    goToStep2(): void {
        this.wizardStep.set(2);
    }

    goToStep1(): void {
        this.wizardStep.set(1);
    }

    /** Advance from Configure to Placement, pre-filling the new-group fields from the source exercise. */
    goToPlacement(): void {
        const src = this.sourceExercise();
        this.newGroupTitle.set((src.title ?? 'Exercise').split(':')[0].trim());
        this.newGroupMaxPoints.set(src.maxPoints);
        this.newGroupReleaseDate.set(this.fmtDate(src.releaseDate));
        this.newGroupStartDate.set(this.fmtDate(src.startDate));
        this.newGroupDueDate.set(this.fmtDate(src.dueDate));
        this.newGroupAssessmentDueDate.set(this.fmtDate(src.assessmentDueDate));
        this.placementChoice.set(this.sourceGroup() ? 'existing-group' : 'new-group');
        this.wizardStep.set(3);
    }

    close(): void {
        this.clearTimers();
        this.wizardStep.set(1);
        this.generationStep.set(0);
        this.generatedVariant.set(null);
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
        this.changeCustom.set(false);
        this.additionalInstructions.set('');
        this.visibleChange.emit(false);
    }

    // ============================================================================================
    // TODO (Opus): Rework this wizard from UI-only mock to the real backend job (plan Section 5.3, point 2):
    //
    // 1. startGeneration(): DELETE the setInterval mock + generateVariant(...) call below. Instead inject
    // ExerciseVariantGenerationService (app/hyperion/services) and:
    // a. Build the request per plan Section 5.1 — intents by FIELD PRESENCE: targetDifficulty only when the
    // difficulty card is selected, domainText/additionalInstructions only when non-blank. Remove the
    // changeDifficulty/changeDomain/changeCustom toggle booleans as request inputs (they remain UI-only
    // card-selection state at most). There is NO title input — the planner names the variant (Section 2.4);
    // remove any title field from the template if present.
    // b. Placement mapping: placementChoice → VariantPlacementDTO (EXISTING_GROUP with sourceGroup id /
    // NEW_GROUP with the newGroup* signals / STANDALONE). For EXAM exercises skip the placement step
    // entirely and send SAME_EXAM_GROUP (plan Section 5.5).
    // c. POST via the service; store the jobId; subscribe to the per-job websocket topic (service handles the
    // hyperion-websocket.service subscribeToJob pattern).
    // 2. Progress steps: replace GENERATION_STEPS with steps DERIVED FROM VariantJobPhase (shared enum via the
    // OpenAPI client — single source of truth, plan Section 5.2). Type-specific sub-labels come from
    // PROGRESS/ATTEMPT events ("Building solution repository — attempt 2/3"). REPAIRING renders as a
    // repeat-visit on the verify step with the attempt counter, NOT as a fake linear step.
    // 3. Expandable step panels: each finished step reveals its StepOutput (STEP_OUTPUT events live; job-detail
    // endpoint on reopen) so instructors can inspect what the LLM planned/did (plan Sections 2.4 and 5.4).
    // 4. DONE handling: fetch the created exercise by variantExerciseId and show the real result step; render
    // DRAFT_WITH_WARNINGS with the warnings listed and a "flagged draft — repair in editor" hint; FAILED shows
    // the failure phase (plan Sections 5.3 and 6).
    // 5. Closable during generation: keep the existing onClose behavior (close ≠ cancel, job continues server-side,
    // no confirmation needed — plan Section 5.4). Add an explicit CANCEL action while running (confirmation
    // dialog; calls service.cancelJob) — distinct from closing (plan Section 5.4).
    // 6. Monitor mode: add an input (e.g. `monitorJobId`) so the tray can reopen this modal for a running/finished
    // job, initialized from GET /variant-jobs/{jobId} — skips wizard steps 1–3 and shows the step timeline
    // (plan Section 5.4).
    // 7. Resume: on open with a source exercise, call the `active` endpoint and re-attach when a job is running
    // (plan Section 5.3, point 5).
    // 8. confirmVariant(): emit variantAdded with the REAL fetched exercise (bound in
    // exercise-actions.component.html — see TODO there); remove the mock path.
    // ============================================================================================
    startGeneration(): void {
        const variant = generateVariant(this.sourceExercise(), {
            changeDifficulty: this.changeDifficulty(),
            targetDifficulty: this.targetDifficulty(),
            changeDomain: this.changeDomain(),
            domainText: this.domainText(),
        });
        this.generatedVariant.set(variant);

        this.wizardStep.set(4);
        this.generationStep.set(0);

        let step = 0;
        this.generationInterval = setInterval(() => {
            step++;
            if (step < GENERATION_STEPS.length) {
                this.generationStep.set(step);
            } else {
                this.clearTimers();
                this.wizardStep.set(5);
            }
        }, STEP_INTERVAL_MS);
    }

    /** TODO: not yet wired to a backend endpoint; the wizard is UI-only for now. */
    confirmVariant(): void {
        const variant = this.generatedVariant();
        if (!variant) return;

        this.variantAdded.emit(variant);
        this.close();
    }

    private fmtDate(d: dayjs.Dayjs | undefined): string {
        return d?.format('YYYY-MM-DDTHH:mm') ?? '';
    }

    private clearTimers(): void {
        if (this.generationInterval !== null) {
            clearInterval(this.generationInterval);
            this.generationInterval = null;
        }
    }
}

import { Component, OnDestroy, computed, inject, input, output, signal } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faPenToSquare, faRobot, faSpinner, faTriangleExclamation, faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { RadioButtonModule } from 'primeng/radiobutton';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DifficultyLevel, Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ExerciseManagementMockService } from 'app/core/course/manage/exercises-experimental/exercise-management-mock.service';
import { GENERATION_STEPS, PlacementChoice, STEP_INTERVAL_MS, difficultyBadgeClass, difficultyLabel, durationDays, generateVariant } from './exercise-variant-ai-modal.utils';

type Phase = 'configure' | 'generating' | 'result';

@Component({
    selector: 'jhi-exercise-variant-ai-modal',
    templateUrl: './exercise-variant-ai-modal.component.html',
    styleUrl: './exercise-variant-ai-modal.component.scss',
    imports: [DialogModule, ButtonModule, CheckboxModule, RadioButtonModule, InputTextModule, TextareaModule, FormsModule, FaIconComponent, SlicePipe],
})
export class ExerciseVariantAiModalComponent implements OnDestroy {
    readonly visible = input<boolean>(false);
    readonly sourceExercise = input.required<Exercise>();
    readonly courseId = input<number | undefined>(undefined);

    readonly visibleChange = output<boolean>();
    readonly variantAdded = output<Exercise>();

    readonly phase = signal<Phase>('configure');
    readonly generationStep = signal(0);
    readonly generatedVariant = signal<ProgrammingExercise | null>(null);
    readonly placementChoice = signal<PlacementChoice>('existing-group');

    readonly changeDifficulty = signal(false);
    readonly targetDifficulty = signal<DifficultyLevel>(DifficultyLevel.MEDIUM);
    readonly changeDomain = signal(false);
    readonly domainText = signal('');
    readonly additionalInstructions = signal('');

    readonly sourceGroup = computed(() => {
        const id = this.sourceExercise().id;
        return id !== undefined ? this.mockService.findGroupForExercise(id) : undefined;
    });

    readonly availableDifficulties = computed<Array<{ value: DifficultyLevel; label: string }>>(() => {
        const current = this.sourceExercise().difficulty;
        return ([DifficultyLevel.EASY, DifficultyLevel.MEDIUM, DifficultyLevel.HARD] as DifficultyLevel[])
            .filter((d) => d !== current)
            .map((d) => ({ value: d, label: difficultyLabel(d) }));
    });

    readonly canGenerate = computed(() => this.changeDifficulty() || this.changeDomain() || this.additionalInstructions().trim().length > 0);

    readonly generationSteps = GENERATION_STEPS;

    protected readonly faRobot = faRobot;
    protected readonly faWandMagicSparkles = faWandMagicSparkles;
    protected readonly faCheck = faCheck;
    protected readonly faPenToSquare = faPenToSquare;
    protected readonly faSpinner = faSpinner;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly DifficultyLevel = DifficultyLevel;
    protected readonly ExerciseType = ExerciseType;
    protected readonly getIcon = getIcon;
    protected readonly durationDays = durationDays;
    protected readonly difficultyBadgeClass = difficultyBadgeClass;

    private generationTimer: ReturnType<typeof setTimeout> | null = null;
    private generationInterval: ReturnType<typeof setInterval> | null = null;

    private readonly mockService = inject(ExerciseManagementMockService);

    ngOnDestroy(): void {
        this.clearTimers();
    }

    onClose(visible: boolean): void {
        if (!visible) {
            this.close();
        }
    }

    close(): void {
        this.clearTimers();
        this.phase.set('configure');
        this.generationStep.set(0);
        this.generatedVariant.set(null);
        this.placementChoice.set('existing-group');
        this.changeDifficulty.set(false);
        this.changeDomain.set(false);
        this.domainText.set('');
        this.additionalInstructions.set('');
        this.visibleChange.emit(false);
    }

    startGeneration(): void {
        const variant = generateVariant(this.sourceExercise(), {
            changeDifficulty: this.changeDifficulty(),
            targetDifficulty: this.targetDifficulty(),
            changeDomain: this.changeDomain(),
            domainText: this.domainText(),
        });
        this.generatedVariant.set(variant);

        this.phase.set('generating');
        this.generationStep.set(0);

        let step = 0;
        this.generationInterval = setInterval(() => {
            step++;
            if (step < GENERATION_STEPS.length) {
                this.generationStep.set(step);
            } else {
                this.clearTimers();
                this.phase.set('result');
                this.placementChoice.set(this.sourceGroup() ? 'existing-group' : 'new-group');
            }
        }, STEP_INTERVAL_MS);
    }

    confirmVariant(): void {
        const variant = this.generatedVariant();
        if (!variant) return;

        const choice = this.placementChoice();
        switch (choice) {
            case 'existing-group': {
                const group = this.sourceGroup();
                if (group?.id !== undefined) {
                    this.mockService.addVariantToGroup(variant, group.id);
                } else {
                    this.mockService.addVariantStandalone(variant);
                }
                break;
            }
            case 'new-group':
                this.mockService.addVariantWithNewGroup(variant, this.sourceExercise());
                break;
            case 'standalone':
                this.mockService.addVariantStandalone(variant);
                break;
        }

        this.variantAdded.emit(variant);
        this.close();
    }

    private clearTimers(): void {
        if (this.generationInterval !== null) {
            clearInterval(this.generationInterval);
            this.generationInterval = null;
        }
        if (this.generationTimer !== null) {
            clearTimeout(this.generationTimer);
            this.generationTimer = null;
        }
    }
}

import { Component, OnDestroy, computed, inject, input, output, signal } from '@angular/core';
import dayjs from 'dayjs/esm';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faEarthAmericas, faGaugeHigh, faPenToSquare, faRobot, faTriangleExclamation, faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { RadioButtonModule } from 'primeng/radiobutton';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DifficultyLevel, Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ExerciseManagementMockService } from 'app/core/course/manage/exercises-experimental/exercise-management-mock.service';
import { GENERATION_STEPS, PlacementChoice, STEP_INTERVAL_MS, difficultyBadgeClass, difficultyLabel, durationDays, generateVariant } from './exercise-variant-ai-modal.utils';

type Phase = 'configure' | 'generating' | 'result';

@Component({
    selector: 'jhi-exercise-variant-ai-modal-cards-inline',
    templateUrl: './exercise-variant-ai-modal-cards-inline.component.html',
    styleUrl: './exercise-variant-ai-modal-cards-inline.component.scss',
    imports: [DialogModule, ButtonModule, RadioButtonModule, InputTextModule, TextareaModule, FormsModule, FaIconComponent, SlicePipe],
})
export class ExerciseVariantAiModalCardsInlineComponent implements OnDestroy {
    readonly visible = input<boolean>(false);
    readonly sourceExercise = input.required<Exercise>();
    readonly courseId = input<number | undefined>(undefined);

    readonly visibleChange = output<boolean>();
    readonly variantAdded = output<Exercise>();

    readonly phase = signal<Phase>('configure');
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

    readonly canGenerate = computed(() => this.changeDifficulty() || this.changeDomain() || (this.changeCustom() && this.additionalInstructions().trim().length > 0));

    readonly showFields = computed(() => this.changeDifficulty() || this.changeDomain() || this.changeCustom());

    readonly generationSteps = GENERATION_STEPS;

    protected readonly faRobot = faRobot;
    protected readonly faWandMagicSparkles = faWandMagicSparkles;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faCheck = faCheck;
    protected readonly faGaugeHigh = faGaugeHigh;
    protected readonly faEarthAmericas = faEarthAmericas;
    protected readonly faPenToSquare = faPenToSquare;
    protected readonly DifficultyLevel = DifficultyLevel;
    protected readonly ExerciseType = ExerciseType;
    protected readonly getIcon = getIcon;
    protected readonly durationDays = durationDays;
    protected readonly difficultyBadgeClass = difficultyBadgeClass;

    private generationInterval: ReturnType<typeof setInterval> | null = null;

    private readonly mockService = inject(ExerciseManagementMockService);

    ngOnDestroy(): void {
        this.clearTimers();
    }

    onClose(visible: boolean): void {
        if (!visible) this.close();
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

    close(): void {
        this.clearTimers();
        this.phase.set('configure');
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

    startGeneration(): void {
        const src = this.sourceExercise();
        this.newGroupTitle.set((src.title ?? 'Exercise').split(':')[0].trim());
        this.newGroupMaxPoints.set(src.maxPoints);
        this.newGroupReleaseDate.set(this.fmtDate(src.releaseDate));
        this.newGroupStartDate.set(this.fmtDate(src.startDate));
        this.newGroupDueDate.set(this.fmtDate(src.dueDate));
        this.newGroupAssessmentDueDate.set(this.fmtDate(src.assessmentDueDate));

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

        switch (this.placementChoice()) {
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
                this.mockService.addVariantWithNewGroup(variant, this.sourceExercise(), {
                    title: this.newGroupTitle(),
                    maxPoints: this.newGroupMaxPoints(),
                    releaseDate: this.newGroupReleaseDate() ? dayjs(this.newGroupReleaseDate()) : undefined,
                    startDate: this.newGroupStartDate() ? dayjs(this.newGroupStartDate()) : undefined,
                    dueDate: this.newGroupDueDate() ? dayjs(this.newGroupDueDate()) : undefined,
                    assessmentDueDate: this.newGroupAssessmentDueDate() ? dayjs(this.newGroupAssessmentDueDate()) : undefined,
                });
                break;
            case 'standalone':
                this.mockService.addVariantStandalone(variant);
                break;
        }

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

import { injectGenerationCapabilities } from 'app/hyperion/exercise-generation/hyperion-generation-capabilities';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faRobot } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseVariantAiModalWizardComponent } from 'app/hyperion/variants/exercise-variant-ai-modal-wizard.component';
import { supportsAiVariantGeneration } from 'app/hyperion/variants/exercise-variant-ai-modal.utils';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

/** Adds the AI variant action to existing Bootstrap detail-page button rows. */
@Component({
    selector: 'jhi-create-variant-with-ai-button',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, ExerciseVariantAiModalWizardComponent],
    styles: `
        :host {
            display: contents;
        }

        jhi-exercise-variant-ai-modal-wizard {
            display: none;
        }
    `,
    template: `
        @if (supported()) {
            <button type="button" class="btn btn-warning btn-sm {{ styleClass() }}" data-testid="create-variant-ai-button" (click)="modalVisible.set(true)">
                <fa-icon [icon]="faRobot" />
                <span jhiTranslate="artemisApp.exerciseManagement.action.createVariantWithAi"></span>
            </button>
            <jhi-exercise-variant-ai-modal-wizard
                [visible]="modalVisible()"
                (visibleChange)="modalVisible.set($event)"
                [sourceExercise]="exercise()"
                [courseId]="resolvedCourseId()"
                [examExercise]="isExamExercise()"
            />
        }
    `,
})
export class CreateVariantWithAiButtonComponent {
    readonly exercise = input.required<Exercise>();

    private readonly hyperionEnabled = inject(ProfileService).isModuleFeatureActive(MODULE_FEATURE_HYPERION);

    /** Spacing utilities from the surrounding button row. */
    readonly styleClass = input<string>('');

    /** Only editors may generate variants, and only for exercise types the generator supports. */
    private readonly generationCapabilities = injectGenerationCapabilities(
        this.exercise,
        computed(() => this.exercise().isAtLeastEditor ?? false),
    );

    readonly supported = computed(
        () =>
            this.hyperionEnabled &&
            (this.exercise().isAtLeastEditor ?? false) &&
            supportsAiVariantGeneration(this.exercise()) &&
            (this.exercise().type !== ExerciseType.PROGRAMMING || this.generationCapabilities.value()?.canCreateVariant === true),
    );

    readonly isExamExercise = computed(() => !!this.exercise().exerciseGroup);

    /** Detail payloads carry the course either directly or through the exercise group's exam. */
    readonly resolvedCourseId = computed(() => this.exercise().course?.id ?? this.exercise().exerciseGroup?.exam?.course?.id);

    protected readonly modalVisible = signal(false);
    protected readonly faRobot = faRobot;
}

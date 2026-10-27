import { injectGenerationCapabilities } from 'app/hyperion/exercise-generation/hyperion-generation-capabilities';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { TumAetUiButtonComponent } from '@tumaet/ui-angular';
import { faRobot } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseVariantAiModalWizardComponent } from 'app/hyperion/variants/exercise-variant-ai-modal-wizard.component';
import { supportsAiVariantGeneration, variantGenerationBlocker } from 'app/hyperion/variants/exercise-variant-ai-modal.utils';
import { MODULE_FEATURE_HYPERION, MODULE_FEATURE_HYPERION_EXERCISE_GENERATION } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

/** Keeps the variant action visible while the server checks whether the saved source can be copied. */
@Component({
    selector: 'jhi-create-variant-with-ai-button',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumAetUiButtonComponent, TranslateDirective, ArtemisTranslatePipe, ExerciseVariantAiModalWizardComponent],
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
            <!-- The reason is a tooltip on the wrapper: a disabled button receives no pointer events of its own. -->
            <!-- White text in both themes, like the Bootstrap warning buttons it stands next to. -->
            <span class="inline-flex flex-col items-start gap-1 {{ styleClass() }}" [attr.title]="blocker() || '' | artemisTranslate" data-testid="create-variant-ai">
                <tumaet-ui-button
                    class="[--tumaet-ui-state-warning-contrast:var(--white)] [&_button]:gap-1! [&_button]:rounded-[0.1rem]! [&_button]:px-2! [&_button]:py-1! [&_button]:text-[0.7875rem]! [&_button]:leading-normal!"
                    severity="warn"
                    variant="solid"
                    size="small"
                    [icon]="faRobot"
                    [disabled]="!!blocker()"
                    [loading]="generationCapabilities.isLoading()"
                    (clicked)="openWizard()"
                    data-testid="create-variant-ai-button"
                >
                    <span jhiTranslate="artemisApp.exerciseManagement.action.createVariantWithAi"></span>
                </tumaet-ui-button>
                @if (canRetry()) {
                    <tumaet-ui-button severity="secondary" variant="text" size="small" (clicked)="generationCapabilities.reload()" data-testid="create-variant-ai-retry">
                        <span jhiTranslate="artemisApp.hyperion.jobs.retry"></span>
                    </tumaet-ui-button>
                }
            </span>
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
    private readonly profiles = inject(ProfileService);
    private readonly hyperionEnabled = this.profiles.isModuleFeatureActive(MODULE_FEATURE_HYPERION);
    private readonly programmingEnabled = this.profiles.isModuleFeatureActive(MODULE_FEATURE_HYPERION_EXERCISE_GENERATION);
    readonly exercise = input.required<Exercise>();

    /** Spacing utilities from the surrounding button row. */
    readonly styleClass = input<string>('');

    /** A caller can require its unsaved edits to be saved before the server copies the source. */
    readonly disabledReason = input<string | undefined>(undefined);

    /** Only editors may generate variants, and only for exercise types the generator supports. */
    readonly generationCapabilities = injectGenerationCapabilities(
        this.exercise,
        computed(() => this.hyperionEnabled && this.programmingEnabled && (this.exercise().isAtLeastEditor ?? false)),
    );

    readonly supported = computed(
        () =>
            this.hyperionEnabled &&
            (this.exercise().isAtLeastEditor ?? false) &&
            !!this.exercise().id &&
            (this.exercise().type === ExerciseType.PROGRAMMING ? this.programmingEnabled && !this.exercise().exerciseGroup : supportsAiVariantGeneration(this.exercise())),
    );

    readonly blocker = computed(
        () => this.disabledReason() ?? variantGenerationBlocker(this.exercise(), this.generationCapabilities.value(), this.generationCapabilities.isLoading()),
    );
    readonly canRetry = computed(
        () =>
            this.exercise().type === ExerciseType.PROGRAMMING &&
            this.blocker() === 'artemisApp.hyperion.generation.blocker.capabilitiesUnavailable' &&
            !this.generationCapabilities.isLoading(),
    );

    openWizard(): void {
        if (this.supported() && !this.blocker()) this.modalVisible.set(true);
    }

    readonly isExamExercise = computed(() => !!this.exercise().exerciseGroup);

    /** Detail payloads carry the course either directly or through the exercise group's exam. */
    readonly resolvedCourseId = computed(() => this.exercise().course?.id ?? this.exercise().exerciseGroup?.exam?.course?.id);

    protected readonly modalVisible = signal(false);
    protected readonly faRobot = faRobot;
}

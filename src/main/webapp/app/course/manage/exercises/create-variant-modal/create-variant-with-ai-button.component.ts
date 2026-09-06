import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faRobot } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseVariantAiModalWizardComponent } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal-wizard.component';
import { supportsAiVariantGeneration } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal.utils';

/**
 * "Create Variant with AI" button plus the wizard it opens, packaged for hosts that lay out their action buttons
 * as markup rather than as `ActionItem` data — currently the programming and quiz exercise detail pages.
 *
 * The exercise table rows keep pushing their own `ActionItem` (their whole action bar is data-driven); this
 * component exists so the detail pages do not each repeat the visibility rule, the modal wiring and the exam /
 * course id resolution.
 *
 * Stays on Bootstrap `.btn` deliberately: both host rows are still all-Bootstrap, and a lone kit button among
 * them looks misplaced. Migrate it together with its hosts.
 */
@Component({
    selector: 'jhi-create-variant-with-ai-button',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, ExerciseVariantAiModalWizardComponent],
    // display: contents lets the button sit in the host's flex row / .btn-group as if it were written there
    // inline. Without it the component element becomes an extra flex item that stretches to the row height and
    // sizes the button differently from its siblings. Spacing utilities therefore go on `styleClass`, not on
    // the host element, which no longer generates a box.
    styles: `
        :host {
            display: contents;
        }

        // The wizard is a mount point only: tum-ui-dialog portals its panel into the CDK overlay container at
        // body level, so this element never renders anything inline. Left visible it would become a SECOND flex
        // item in the host's button row (display: contents promotes every child) and consume another \`gap\`
        // slot, doubling the space between this button and the next one.
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

    /** Spacing utilities of the surrounding button row, applied to the button itself (see the :host note). */
    readonly styleClass = input<string>('');

    /** Only editors may generate variants, and only for exercise types the generator supports. */
    readonly supported = computed(() => (this.exercise().isAtLeastEditor ?? false) && supportsAiVariantGeneration(this.exercise()));

    readonly isExamExercise = computed(() => !!this.exercise().exerciseGroup);

    /** Detail payloads carry the course either directly or through the exercise group's exam. */
    readonly resolvedCourseId = computed(() => this.exercise().course?.id ?? this.exercise().exerciseGroup?.exam?.course?.id);

    protected readonly modalVisible = signal(false);
    protected readonly faRobot = faRobot;
}

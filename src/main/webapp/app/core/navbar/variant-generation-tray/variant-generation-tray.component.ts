import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSpinner, faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';
import { PopoverModule } from 'primeng/popover';
import { ButtonModule } from 'primeng/button';
import { ExerciseVariantGenerationService } from 'app/hyperion/services/exercise-variant-generation.service';

/**
 * Navbar job tray for background variant generation (plan Section 5.4).
 * Mounted in navbar.component.html. Tray = state at a glance; the generation modal = full inspection.
 */
@Component({
    selector: 'jhi-variant-generation-tray',
    templateUrl: './variant-generation-tray.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, PopoverModule, ButtonModule],
})
export class VariantGenerationTrayComponent {
    protected readonly variantGenerationService = inject(ExerciseVariantGenerationService);

    protected readonly faWandMagicSparkles = faWandMagicSparkles;
    protected readonly faSpinner = faSpinner;

    // TODO (Sonnet): Implement per plan Section 5.4:
    // - Visibility: hidden entirely when variantGenerationService.hasJobs() is false (use @if in the navbar or here).
    // - Icon button (faWandMagicSparkles); while runningJobs().length > 0 overlay a spinner ring
    // (fa-spinner fa-spin badge) — the at-a-glance "AI is generating in the background" signal — plus a count
    // badge with the number of RUNNING jobs. Use Bootstrap utility classes, no custom SCSS.
    // - Click opens the p-popover listing variantGenerationService.jobs():
    // * running entries: source exercise title, current phase label (derive labels from VariantJobPhase — the
    // shared enum, plan Section 5.2), slim progress bar (phase index / total), attempt counter during
    // REPAIRING, cancel action behind a confirmation dialog (PrimeNG ConfirmDialog/DialogService — NOT
    // ng-bootstrap). State only — no step outputs in the tray.
    // * finished entries stay listed (server TTL): COMPLETED → generated variant title + deep link to the
    // type-aware editor route (programming → exercise detail/editor, quiz → quiz editor);
    // DRAFT_WITH_WARNINGS → warning badge + same link; FAILED → failure phase label;
    // CANCELLED → neutral state, NO link (clone was cleaned up).
    // * clicking any entry reopens the generation modal in monitor mode, initialized from
    // getJobDetail(jobId) — full step timeline with expandable step outputs (plan Sections 2.4 and 5.4).
    // Emit an output / use a shared service the wizard host listens to; the wizard component gains a
    // "monitor" input mode (see exercise-variant-ai-modal-wizard.component.ts TODOs).
    //
    // TODO (Sonnet): i18n — all labels via artemisTranslate with new keys under artemisApp.exerciseVariant.tray.*
    // (add to both en/de global.json).
}

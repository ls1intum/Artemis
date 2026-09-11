import { ChangeDetectionStrategy, Component, ElementRef, computed, input, model, output, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faCheckDouble, faChevronDown, faPaperPlane, faSpinner, faTimes, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import {
    TumUiButtonDirective,
    TumUiButtonGroupComponent,
    TumUiDisabledReasonDirective,
    TumUiInputDirective,
    TumUiMenuComponent,
    TumUiMenuItemDirective,
    TumUiMenuTriggerDirective,
    TumUiPopoverComponent,
    TumUiStatusDotComponent,
    TumUiTagComponent,
} from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { MAX_USER_PROMPT_LENGTH, PROMPT_LENGTH_WARNING_THRESHOLD } from 'app/programming/manage/shared/problem-statement.utils';

/**
 * The code editor's AI actions: **Adapt exercise** as the primary action and a menu with the problem-statement
 * refinement and the consistency check. Nothing here is hidden by state - an action that cannot run right now is
 * disabled and says why, so the instructor learns what to change rather than where the button went.
 *
 * Presentational: the container owns every decision (feature flags, eligibility, run state) and passes the outcome in
 * as a translation key per action; `undefined` means the action is available.
 */
@Component({
    selector: 'jhi-code-editor-ai-actions',
    templateUrl: './code-editor-ai-actions.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        RouterLink,
        FaIconComponent,
        ArtemisTranslatePipe,
        TranslateDirective,
        TumUiButtonDirective,
        TumUiButtonGroupComponent,
        TumUiDisabledReasonDirective,
        TumUiMenuComponent,
        TumUiMenuItemDirective,
        TumUiMenuTriggerDirective,
        TumUiPopoverComponent,
        TumUiStatusDotComponent,
        TumUiTagComponent,
        TumUiInputDirective,
    ],
})
export class CodeEditorAiActionsComponent {
    /** Whether this deployment offers whole-exercise adaptation. Without it the group is only the menu. */
    readonly adaptOffered = input(false);
    /** Translation key for why adaptation cannot start now; `undefined` when it can. */
    readonly adaptBlockedReason = input<string | undefined>();
    /** Review comments already selected as feedback, shown on the primary action so a selection is never invisible. */
    readonly selectedFeedbackCount = input(0);
    /** Route to the generation page while a run, or a lost run status, needs the instructor's attention. */
    readonly progressLink = input<readonly unknown[] | undefined>();
    readonly progressRunning = input(false);
    /** Whether the problem statement is still the template, which turns "refine" into "generate". */
    readonly problemStatementEmpty = input(false);
    readonly refineBlockedReason = input<string | undefined>();
    readonly consistencyBlockedReason = input<string | undefined>();
    /** Open consistency issues; shown on the menu trigger so they are noticed without opening it. */
    readonly issueCount = input(0);
    /** Any AI operation in flight: shown as a spinner on the menu trigger. */
    readonly busy = input(false);
    /** A cancellable problem-statement operation is running. */
    readonly cancelAvailable = input(false);
    /** The refinement prompt, kept across popover dismissals so typed text is not lost. */
    readonly prompt = model('');

    readonly adaptRequested = output<void>();
    readonly refineSubmitted = output<void>();
    readonly consistencyRequested = output<void>();
    readonly issuesToggled = output<void>();
    readonly cancelRequested = output<void>();

    protected readonly MAX_USER_PROMPT_LENGTH = MAX_USER_PROMPT_LENGTH;
    protected readonly facArtemisIntelligence = facArtemisIntelligence;
    protected readonly faBan = faBan;
    protected readonly faCheckDouble = faCheckDouble;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faPaperPlane = faPaperPlane;
    protected readonly faSpinner = faSpinner;
    protected readonly faTimes = faTimes;
    protected readonly faTriangleExclamation = faTriangleExclamation;

    private readonly refinementPopover = viewChild.required<TumUiPopoverComponent>('refinementPopover');
    // `tumUiButton` is a component, so the reference must be read as the element for the popover to anchor to it.
    private readonly menuTrigger = viewChild.required('menuTrigger', { read: ElementRef<HTMLElement> });

    protected readonly refineLabelKey = computed(() =>
        this.problemStatementEmpty()
            ? 'artemisApp.programmingExercise.problemStatement.generationAssistance'
            : 'artemisApp.programmingExercise.problemStatement.refinementAssistance',
    );
    protected readonly refineSubmitKey = computed(() =>
        this.problemStatementEmpty() ? 'artemisApp.programmingExercise.problemStatement.generate' : 'artemisApp.programmingExercise.problemStatement.refine',
    );
    protected readonly promptNearLimit = computed(() => this.prompt().length >= MAX_USER_PROMPT_LENGTH * PROMPT_LENGTH_WARNING_THRESHOLD);
    protected readonly promptSubmittable = computed(() => this.prompt().trim().length > 0 && !this.refineBlockedReason());
    protected readonly issueCountLabel = computed(() => String(this.issueCount()));

    protected requestAdapt(): void {
        if (!this.adaptBlockedReason()) {
            this.adaptRequested.emit();
        }
    }

    /** Opens the prompt next to the trigger the menu was opened from, so the popover appears where the instructor was. */
    protected openRefinement(): void {
        this.refinementPopover().open(this.menuTrigger());
    }

    protected submitRefinement(): void {
        if (!this.promptSubmittable()) {
            return;
        }
        this.refinementPopover().close();
        this.refineSubmitted.emit();
    }

    /** Closes the refinement prompt, e.g. when the exercise changes underneath the editor. The typed text is kept. */
    close(): void {
        this.refinementPopover().close();
    }
}

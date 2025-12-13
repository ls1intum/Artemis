import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faChevronDown, faChevronRight, faFloppyDisk, faSpinner, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { facArtemisIntelligence } from 'app/shared/icons/icons';
import { InlineComment, createInlineComment } from '../model/inline-comment.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

/**
 * Widget component for creating and editing inline comments on problem statements.
 */
@Component({
    selector: 'jhi-inline-comment-widget',
    standalone: true,
    imports: [FormsModule, FaIconComponent, TranslateDirective, ArtemisTranslatePipe, SlicePipe],
    templateUrl: './inline-comment-widget.component.html',
    styleUrls: ['./inline-comment-widget.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InlineCommentWidgetComponent {
    /** Start line number (1-indexed) */
    startLine = input.required<number>();

    /** End line number (1-indexed, inclusive) */
    endLine = input.required<number>();

    /** Existing comment to edit (optional) */
    existingComment = input<InlineComment | undefined>();

    /** Whether the AI is currently applying this comment */
    isApplying = input<boolean>(false);

    /** Whether the widget should be read-only */
    readOnly = input<boolean>(false);

    /** Whether the widget should start collapsed (for saved comments) */
    collapsed = input<boolean>(false);

    /** Emits when user saves the comment for later */
    onSave = output<InlineComment>();

    /** Emits when user wants to apply immediately with AI */
    onApply = output<InlineComment>();

    /** Emits when user cancels/closes the widget */
    onCancel = output<void>();

    /** Emits when user deletes an existing comment */
    onDelete = output<string>();

    // Icons
    protected readonly faTimes = faTimes;
    protected readonly faFloppyDisk = faFloppyDisk;
    protected readonly faSpinner = faSpinner;
    protected readonly faBan = faBan;
    protected readonly facArtemisIntelligence = facArtemisIntelligence;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronRight = faChevronRight;

    // Local State

    /** Current instruction text */
    protected instruction = signal('');

    /** Whether the widget is currently collapsed */
    protected isCollapsed = signal(false);

    /** Flag to track if instruction has been initialized (to avoid overwriting user edits) */
    private instructionInitialized = false;

    // Injected Services
    private translateService = inject(TranslateService);

    // Computed Properties

    protected lineLabel = computed(() => {
        const start = this.startLine();
        const end = this.endLine();
        if (start === end) {
            return this.translateService.instant('artemisApp.programmingExercise.inlineComment.line', { line: start });
        } else {
            return this.translateService.instant('artemisApp.programmingExercise.inlineComment.lines', { start, end });
        }
    });

    /** Whether the submit actions are enabled */
    protected canSubmit = computed(() => {
        return this.instruction().trim().length > 0 && !this.isApplying() && !this.readOnly();
    });

    /** Whether we're editing an existing comment */
    protected isEditing = computed(() => !!this.existingComment());

    // Lifecycle

    constructor() {
        // Use effect to initialize instruction from existingComment when inputs are set
        effect(() => {
            const existing = this.existingComment();
            if (existing && !this.instructionInitialized) {
                this.instruction.set(existing.instruction);
                this.instructionInitialized = true;
            }
        });

        // Use effect to initialize collapsed state from input
        effect(() => {
            if (this.collapsed()) {
                this.isCollapsed.set(true);
            }
        });
    }

    /**
     * Toggles the collapsed/expanded state.
     */
    protected toggleCollapse(): void {
        this.isCollapsed.set(!this.isCollapsed());
    }

    // Actions

    /**
     * Saves the comment for later (adds to pending list).
     */
    protected handleSave(): void {
        if (!this.canSubmit()) {
            return;
        }

        const comment = this.createOrUpdateComment('pending');
        this.onSave.emit(comment);
    }

    /**
     * Applies the comment immediately with AI.
     */
    protected handleApply(): void {
        if (!this.canSubmit()) {
            return;
        }

        const comment = this.createOrUpdateComment('applying');
        this.onApply.emit(comment);
    }

    /**
     * Cancels and closes the widget.
     */
    protected handleCancel(): void {
        this.onCancel.emit();
    }

    /**
     * Deletes an existing comment.
     */
    protected handleDelete(): void {
        const existing = this.existingComment();
        if (existing) {
            this.onDelete.emit(existing.id);
        }
    }

    // Helpers

    private createOrUpdateComment(status: 'pending' | 'applying'): InlineComment {
        const existing = this.existingComment();

        if (existing) {
            return {
                ...existing,
                instruction: this.instruction().trim(),
                status,
            };
        }

        const newComment = createInlineComment(this.startLine(), this.endLine(), this.instruction().trim());
        return { ...newComment, status };
    }
}

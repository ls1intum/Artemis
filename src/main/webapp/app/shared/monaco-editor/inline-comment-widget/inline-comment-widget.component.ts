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

    /** Emits when user wants to cancel an in-progress apply operation */
    onCancelApply = output<void>();

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

    /** Internal applying state - set when Apply is clicked */
    private _isApplyingInternal = signal(false);

    /** Tracks the comment created on first Save/Apply (for retry scenarios) */
    private _createdComment = signal<InlineComment | undefined>(undefined);

    /** Combined applying state from input or internal */
    protected isApplyingCombined = computed(() => this.isApplying() || this._isApplyingInternal());

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
        return this.instruction().trim().length > 0 && !this.isApplyingCombined() && !this.readOnly();
    });

    /** Whether we're editing an existing comment */
    protected isEditing = computed(() => !!this.existingComment() || !!this._createdComment());

    /** Whether we have any comment (from input or created internally) - used for collapse button */
    protected hasComment = computed(() => !!this.existingComment() || !!this._createdComment());

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
        // Collapse the widget after saving
        this.isCollapsed.set(true);
    }

    /**
     * Applies the comment immediately with AI.
     */
    protected handleApply(): void {
        if (!this.canSubmit()) {
            return;
        }

        // Set internal applying state to disable UI
        this._isApplyingInternal.set(true);

        const comment = this.createOrUpdateComment('applying');
        this.onApply.emit(comment);
    }

    /**
     * Cancels and closes the widget.
     * If an apply operation is in progress, cancels it but keeps the widget open.
     * If the comment has been saved, collapses the widget instead of closing.
     */
    protected handleCancel(): void {
        if (this._isApplyingInternal()) {
            // Cancel the in-progress apply operation but keep widget open
            this.onCancelApply.emit();
            this._isApplyingInternal.set(false);
            return; // Don't close the widget - let user try again
        }

        // If we have a saved comment, just collapse instead of closing
        if (this.hasComment()) {
            this.isCollapsed.set(true);
            return;
        }

        // Only close widget if it's a fresh unsaved comment
        this.onCancel.emit();
    }

    /**
     * Deletes an existing comment.
     */
    protected handleDelete(): void {
        // Check both input comment and internally created comment
        const commentId = this.existingComment()?.id ?? this._createdComment()?.id;
        if (commentId) {
            this.onDelete.emit(commentId);
        }
    }

    // Helpers

    private createOrUpdateComment(status: 'pending' | 'applying'): InlineComment {
        // First check if we have a previously created comment (for retries)
        const createdComment = this._createdComment();
        if (createdComment) {
            const updated = {
                ...createdComment,
                instruction: this.instruction().trim(),
                status,
            };
            this._createdComment.set(updated);
            return updated;
        }

        // Check for existing comment from input
        const existing = this.existingComment();
        if (existing) {
            return {
                ...existing,
                instruction: this.instruction().trim(),
                status,
            };
        }

        // Create new comment and track it for retries
        const newComment = createInlineComment(this.startLine(), this.endLine(), this.instruction().trim());
        const tracked = { ...newComment, status };
        this._createdComment.set(tracked);
        return tracked;
    }
}

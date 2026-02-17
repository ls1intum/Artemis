import { ComponentRef, ViewContainerRef } from '@angular/core';
import { ReviewCommentFacade } from 'app/exercise/review/review-comment-facade.service';
import { ReviewCommentDraftLocation } from 'app/exercise/review/review-comment.store';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ReviewCommentDraftWidgetComponent } from 'app/exercise/review/review-comment-draft-widget/review-comment-draft-widget.component';
import { ReviewCommentThreadWidgetComponent } from 'app/exercise/review/review-comment-thread-widget/review-comment-thread-widget.component';
import { CommentThread, CreateCommentThread } from 'app/exercise/shared/entities/review/comment-thread.model';
import { CreateComment } from 'app/exercise/shared/entities/review/comment.model';

type ReviewCommentDraftPayload = { lineNumber: number; fileName: string };

export type ReviewCommentWidgetManagerConfig = {
    hoverButtonClass: string;
    shouldShowHoverButton: () => boolean;
    canSubmit: () => boolean;
    getDraftFileName: () => string | undefined;
    buildDraftLocation: (payload: ReviewCommentDraftPayload) => ReviewCommentDraftLocation | undefined;
    buildCreateThreadRequest: (payload: ReviewCommentDraftPayload & { initialComment: CreateComment }) => CreateCommentThread | undefined;
    filterThread: (thread: CommentThread) => boolean;
    getThreadLine: (thread: CommentThread) => number;
    showLocationWarning: () => boolean;
};

export class ReviewCommentWidgetManager {
    private readonly draftLinesByFile: Map<string, Set<number>> = new Map();
    private readonly draftWidgetRefs: Map<string, ComponentRef<ReviewCommentDraftWidgetComponent>> = new Map();
    private readonly threadWidgetRefs: Map<number, ComponentRef<ReviewCommentThreadWidgetComponent>> = new Map();
    private readonly collapseState: Map<number, boolean> = new Map();
    private readonly editingCommentIdByThread: Map<number, number> = new Map();

    constructor(
        private readonly editor: MonacoEditorComponent,
        private readonly viewContainerRef: ViewContainerRef,
        private readonly reviewCommentFacade: ReviewCommentFacade,
        private readonly config: ReviewCommentWidgetManagerConfig,
    ) {
        this.editor.onDidScrollChange(() => this.hideThreadMenus());
    }

    /**
     * Updates the hover button visibility and callback based on configuration.
     */
    updateHoverButton(): void {
        if (this.config.shouldShowHoverButton()) {
            this.editor.setLineDecorationsHoverButton(this.config.hoverButtonClass, (lineNumber) => this.addDraft(lineNumber));
        } else {
            this.editor.clearLineDecorationsHoverButton();
        }
    }

    /**
     * Renders both saved thread widgets and active draft widgets.
     */
    renderWidgets(): void {
        this.addSavedWidgets();
        this.addDraftWidgets();
    }

    /**
     * Pushes the latest submit availability to all draft widgets.
     */
    updateDraftInputs(): void {
        this.synchronizeDraftWidgetsWithState();
        const canSubmit = this.config.canSubmit();
        this.draftWidgetRefs.forEach((ref, widgetKey) => {
            const { fileName, line } = this.parseDraftKey(widgetKey);
            const payload = { lineNumber: line + 1, fileName };
            ref.setInput('canSubmit', canSubmit);
            ref.setInput('text', this.getDraftText(payload));
            ref.setInput('isSubmitting', this.isDraftSubmitting(payload));
        });
    }

    /**
     * Updates thread inputs in-place when widgets already exist.
     *
     * @returns True if all widgets were updated, false if any were missing.
     */
    updateThreadInputs(): boolean {
        const threads = this.reviewCommentFacade.threads().filter((thread) => this.config.filterThread(thread));
        const threadIds = new Set<number>(threads.map((thread) => thread.id));
        let updated = true;
        for (const [threadId, ref] of this.threadWidgetRefs.entries()) {
            if (!threadIds.has(threadId)) {
                this.disposeThreadWidget(threadId, ref);
            }
        }
        for (const thread of threads) {
            const widgetRef = this.threadWidgetRefs.get(thread.id);
            if (!widgetRef) {
                updated = false;
                continue;
            }
            widgetRef.setInput('thread', thread);
            widgetRef.setInput('showLocationWarning', this.config.showLocationWarning());
            widgetRef.setInput('replyText', this.reviewCommentFacade.getReplyDraft(thread.id));
            widgetRef.setInput('isReplySubmitting', this.reviewCommentFacade.isReplySubmitting(thread.id));
            widgetRef.setInput('isResolveSubmitting', this.reviewCommentFacade.isResolveSubmitting(thread.id));

            const editingCommentId = this.editingCommentIdByThread.get(thread.id);
            if (editingCommentId !== undefined && !thread.comments?.some((comment) => comment.id === editingCommentId)) {
                this.editingCommentIdByThread.delete(thread.id);
            }
            const activeEditingCommentId = this.editingCommentIdByThread.get(thread.id);
            widgetRef.setInput('editText', activeEditingCommentId !== undefined ? this.reviewCommentFacade.getEditDraft(activeEditingCommentId) : '');
            widgetRef.setInput('isEditSubmitting', activeEditingCommentId !== undefined ? this.reviewCommentFacade.isEditSubmitting(activeEditingCommentId) : false);
        }
        return updated;
    }

    /**
     * Disposes all widgets and clears internal tracking state.
     */
    disposeAll(): void {
        // Ensure Monaco view zones are removed when review comments are disabled/unmounted.
        this.editor.disposeWidgetsByPrefix('review-comment-');
        this.disposeDraftWidgets();
        this.disposeSavedWidgets();
        this.draftLinesByFile.clear();
        this.collapseState.clear();
        this.editingCommentIdByThread.clear();
    }

    /**
     * Removes draft widgets and clears draft line tracking.
     */
    clearDrafts(): void {
        for (const [fileName, lines] of this.draftLinesByFile.entries()) {
            for (const line of lines) {
                this.cancelDraft({ lineNumber: line + 1, fileName });
                this.editor.disposeWidgetsByPrefix(this.buildDraftWidgetId(fileName, line));
            }
        }
        this.disposeDraftWidgets();
        this.draftLinesByFile.clear();
    }

    /**
     * Creates or updates a draft widget at the given 1-based line number.
     *
     * @param lineNumber The 1-based line number where the draft widget should be rendered.
     */
    private addDraft(lineNumber: number): void {
        const fileName = this.config.getDraftFileName();
        if (!fileName) {
            return;
        }
        const lineNumberZeroBased = lineNumber - 1;
        const draftPayload = { lineNumber, fileName };
        const existing = this.draftLinesByFile.get(fileName) ?? new Set<number>();
        if (!existing.has(lineNumberZeroBased)) {
            existing.add(lineNumberZeroBased);
            this.draftLinesByFile.set(fileName, existing);
        }
        const widgetKey = this.getDraftKey(fileName, lineNumberZeroBased);
        let widgetRef = this.draftWidgetRefs.get(widgetKey);
        if (!widgetRef) {
            const createdWidgetRef = this.viewContainerRef.createComponent(ReviewCommentDraftWidgetComponent);
            createdWidgetRef.instance.onSubmitDraft.subscribe(() => this.submitDraft(fileName, lineNumberZeroBased));
            createdWidgetRef.instance.onTextChange.subscribe((text) => {
                this.setDraftText(draftPayload, text);
            });
            createdWidgetRef.instance.onCancel.subscribe(() => this.removeDraft(fileName, lineNumberZeroBased));
            this.draftWidgetRefs.set(widgetKey, createdWidgetRef);
            widgetRef = createdWidgetRef;
        }
        if (!widgetRef) {
            return;
        }
        widgetRef.setInput('canSubmit', this.config.canSubmit());
        widgetRef.setInput('text', this.getDraftText(draftPayload));
        widgetRef.setInput('isSubmitting', this.isDraftSubmitting(draftPayload));
        // Re-adding the same draft line must replace the existing Monaco widget to avoid stacked view zones.
        this.editor.disposeWidgetsByPrefix(this.buildDraftWidgetId(fileName, lineNumberZeroBased));
        this.editor.addLineWidget(lineNumber, this.buildDraftWidgetId(fileName, lineNumberZeroBased), widgetRef.location.nativeElement);
        this.ensureDraft(draftPayload);
    }

    /**
     * Renders draft widgets for the active file based on tracked draft lines.
     */
    private addDraftWidgets(): void {
        this.synchronizeDraftWidgetsWithState();
        const activeFileName = this.config.getDraftFileName();
        if (!activeFileName) {
            return;
        }
        const lines = this.draftLinesByFile.get(activeFileName);
        if (!lines) {
            return;
        }
        for (const line of lines) {
            const lineNumber = line + 1;
            const draftPayload = { lineNumber, fileName: activeFileName };
            const widgetKey = this.getDraftKey(activeFileName, line);
            let widgetRef = this.draftWidgetRefs.get(widgetKey);
            if (!widgetRef) {
                const createdWidgetRef = this.viewContainerRef.createComponent(ReviewCommentDraftWidgetComponent);
                createdWidgetRef.instance.onSubmitDraft.subscribe(() => this.submitDraft(activeFileName, line));
                createdWidgetRef.instance.onTextChange.subscribe((text) => {
                    this.setDraftText(draftPayload, text);
                });
                createdWidgetRef.instance.onCancel.subscribe(() => this.removeDraft(activeFileName, line));
                this.draftWidgetRefs.set(widgetKey, createdWidgetRef);
                widgetRef = createdWidgetRef;
            }
            if (!widgetRef) {
                continue;
            }
            widgetRef.setInput('canSubmit', this.config.canSubmit());
            widgetRef.setInput('text', this.getDraftText(draftPayload));
            widgetRef.setInput('isSubmitting', this.isDraftSubmitting(draftPayload));
            // Keep rendering idempotent when renderWidgets() runs repeatedly for the same draft.
            this.editor.disposeWidgetsByPrefix(this.buildDraftWidgetId(activeFileName, line));
            this.editor.addLineWidget(lineNumber, this.buildDraftWidgetId(activeFileName, line), widgetRef.location.nativeElement);
        }
    }

    /**
     * Renders thread widgets, disposing stale ones and preserving collapse state.
     */
    private addSavedWidgets(): void {
        const threads = this.reviewCommentFacade.threads().filter((thread) => this.config.filterThread(thread));
        const threadIds = new Set<number>();
        for (const thread of threads) {
            threadIds.add(thread.id);
        }
        for (const [threadId, ref] of this.threadWidgetRefs.entries()) {
            if (!threadIds.has(threadId)) {
                this.disposeThreadWidget(threadId, ref);
            }
        }
        for (const thread of threads) {
            const line = this.config.getThreadLine(thread);
            const widgetId = this.buildThreadWidgetId(thread.id);
            const showLocationWarning = this.config.showLocationWarning();
            let widgetRef = this.threadWidgetRefs.get(thread.id);
            if (!widgetRef) {
                const createdWidgetRef = this.viewContainerRef.createComponent(ReviewCommentThreadWidgetComponent);
                createdWidgetRef.setInput('thread', thread);
                createdWidgetRef.setInput('showLocationWarning', showLocationWarning);
                createdWidgetRef.setInput('replyText', this.reviewCommentFacade.getReplyDraft(thread.id));
                createdWidgetRef.setInput('isReplySubmitting', this.reviewCommentFacade.isReplySubmitting(thread.id));
                createdWidgetRef.setInput('isResolveSubmitting', this.reviewCommentFacade.isResolveSubmitting(thread.id));
                if (!this.collapseState.has(thread.id)) {
                    const shouldCollapse = thread.resolved || showLocationWarning;
                    this.collapseState.set(thread.id, shouldCollapse);
                }
                createdWidgetRef.setInput('initialCollapsed', this.collapseState.get(thread.id) ?? false);
                createdWidgetRef.instance.onDelete.subscribe((commentId) => {
                    if (this.reviewCommentFacade.isDeleteSubmitting(commentId)) {
                        return;
                    }
                    this.reviewCommentFacade.deleteComment(commentId);
                });
                createdWidgetRef.instance.onReplyTextChange.subscribe((text) => {
                    this.reviewCommentFacade.setReplyDraft(thread.id, text);
                });
                createdWidgetRef.instance.onSubmitReply.subscribe(() => this.submitReply(thread.id));
                createdWidgetRef.instance.onStartEdit.subscribe((payload) => {
                    this.editingCommentIdByThread.set(thread.id, payload.commentId);
                    this.reviewCommentFacade.startEditDraft(payload.commentId, payload.initialText);
                    createdWidgetRef.setInput('editText', this.reviewCommentFacade.getEditDraft(payload.commentId));
                    createdWidgetRef.setInput('isEditSubmitting', this.reviewCommentFacade.isEditSubmitting(payload.commentId));
                });
                createdWidgetRef.instance.onEditTextChange.subscribe((payload) => {
                    this.reviewCommentFacade.setEditDraft(payload.commentId, payload.text);
                });
                createdWidgetRef.instance.onCancelEdit.subscribe((commentId) => {
                    this.reviewCommentFacade.cancelEditDraft(commentId);
                    if (this.editingCommentIdByThread.get(thread.id) === commentId) {
                        this.editingCommentIdByThread.delete(thread.id);
                    }
                    createdWidgetRef.setInput('editText', '');
                    createdWidgetRef.setInput('isEditSubmitting', false);
                });
                createdWidgetRef.instance.onSubmitEdit.subscribe((commentId) => this.submitEdit(thread.id, commentId));
                createdWidgetRef.instance.onToggleResolved.subscribe((resolved) => {
                    if (this.reviewCommentFacade.isResolveSubmitting(thread.id)) {
                        return;
                    }
                    this.reviewCommentFacade.toggleResolved(thread.id, resolved);
                });
                createdWidgetRef.instance.onToggleCollapse.subscribe((collapsed) => this.collapseState.set(thread.id, collapsed));
                this.threadWidgetRefs.set(thread.id, createdWidgetRef);
                widgetRef = createdWidgetRef;
            } else {
                widgetRef.setInput('thread', thread);
                widgetRef.setInput('showLocationWarning', showLocationWarning);
                widgetRef.setInput('replyText', this.reviewCommentFacade.getReplyDraft(thread.id));
                widgetRef.setInput('isReplySubmitting', this.reviewCommentFacade.isReplySubmitting(thread.id));
                widgetRef.setInput('isResolveSubmitting', this.reviewCommentFacade.isResolveSubmitting(thread.id));
            }
            if (!widgetRef) {
                continue;
            }

            const editingCommentId = this.editingCommentIdByThread.get(thread.id);
            if (editingCommentId !== undefined) {
                widgetRef.setInput('editText', this.reviewCommentFacade.getEditDraft(editingCommentId));
                widgetRef.setInput('isEditSubmitting', this.reviewCommentFacade.isEditSubmitting(editingCommentId));
            } else {
                widgetRef.setInput('editText', '');
                widgetRef.setInput('isEditSubmitting', false);
            }
            this.editor.disposeWidgetsByPrefix(widgetId);
            this.editor.addLineWidget(line + 1, widgetId, widgetRef.location.nativeElement);
        }
    }

    /**
     * Emits the draft submit callback when the stored draft text is non-empty.
     *
     * @param fileName The file where the draft was created.
     * @param line The zero-based line index of the draft.
     */
    private submitDraft(fileName: string, line: number): void {
        const payload = { lineNumber: line + 1, fileName };
        if (this.isDraftSubmitting(payload) || !this.config.canSubmit()) {
            return;
        }
        const text = this.getDraftText(payload).trim();
        if (!text) {
            return;
        }
        const request = this.config.buildCreateThreadRequest({ ...payload, initialComment: { contentType: 'USER', text } });
        if (!request) {
            return;
        }
        this.reviewCommentFacade.submitCreateThread(request);
    }

    /**
     * Emits a reply submit callback when reply draft text is non-empty.
     *
     * @param threadId The thread id receiving the reply.
     */
    private submitReply(threadId: number): void {
        if (this.reviewCommentFacade.isReplySubmitting(threadId)) {
            return;
        }
        const text = this.reviewCommentFacade.getReplyDraft(threadId).trim();
        if (!text) {
            return;
        }
        this.reviewCommentFacade.createReply(threadId, { contentType: 'USER', text });
    }

    /**
     * Emits an edit submit callback when edit draft text is non-empty.
     *
     * @param threadId The thread id containing the edited comment.
     * @param commentId The comment id being edited.
     */
    private submitEdit(threadId: number, commentId: number): void {
        if (this.reviewCommentFacade.isEditSubmitting(commentId)) {
            return;
        }
        const text = this.reviewCommentFacade.getEditDraft(commentId).trim();
        if (!text) {
            return;
        }
        this.reviewCommentFacade.updateComment(commentId, { contentType: 'USER', text });
        this.editingCommentIdByThread.delete(threadId);
    }

    /**
     * Removes the draft widget and tracking entry for a file/line pair.
     *
     * @param fileName The file where the draft was created.
     * @param line The zero-based line index of the draft.
     */
    private removeDraft(fileName: string, line: number, notifyCancel: boolean = true): void {
        if (notifyCancel) {
            this.cancelDraft({ lineNumber: line + 1, fileName });
        }
        const existing = this.draftLinesByFile.get(fileName);
        if (existing) {
            existing.delete(line);
            if (existing.size === 0) {
                this.draftLinesByFile.delete(fileName);
            }
        }
        const widgetKey = this.getDraftKey(fileName, line);
        this.editor.disposeWidgetsByPrefix(this.buildDraftWidgetId(fileName, line));
        this.draftWidgetRefs.get(widgetKey)?.destroy();
        this.draftWidgetRefs.delete(widgetKey);
    }

    /**
     * Disposes all draft widget component refs.
     */
    private disposeDraftWidgets(): void {
        this.draftWidgetRefs.forEach((ref) => {
            ref.destroy();
        });
        this.draftWidgetRefs.clear();
    }

    /**
     * Disposes all thread widget component refs.
     */
    private disposeSavedWidgets(): void {
        this.threadWidgetRefs.forEach((ref) => {
            ref.destroy();
        });
        this.threadWidgetRefs.clear();
        this.collapseState.clear();
        this.editingCommentIdByThread.clear();
    }

    /**
     * Disposes a specific thread widget and clears associated local state.
     */
    private disposeThreadWidget(threadId: number, ref?: ComponentRef<ReviewCommentThreadWidgetComponent>): void {
        this.editor.disposeWidgetsByPrefix(this.buildThreadWidgetId(threadId));
        (ref ?? this.threadWidgetRefs.get(threadId))?.destroy();
        this.threadWidgetRefs.delete(threadId);
        this.collapseState.delete(threadId);
        this.editingCommentIdByThread.delete(threadId);
    }

    /**
     * Hides all open thread action menus, e.g. when editor scrolling changes widget positions.
     */
    private hideThreadMenus(): void {
        this.threadWidgetRefs.forEach((ref) => {
            ref.instance.hideCommentMenus();
        });
    }

    /**
     * Removes draft widgets that no longer exist in external state.
     */
    private synchronizeDraftWidgetsWithState(): void {
        for (const [fileName, lines] of this.draftLinesByFile.entries()) {
            for (const line of [...lines]) {
                const payload = { lineNumber: line + 1, fileName };
                if (!this.hasDraft(payload)) {
                    this.removeDraft(fileName, line, false);
                }
            }
        }
    }

    /**
     * Builds a store draft location from manager payload.
     */
    private buildDraftLocation(payload: ReviewCommentDraftPayload): ReviewCommentDraftLocation | undefined {
        return this.config.buildDraftLocation(payload);
    }

    /**
     * Checks whether a draft exists for the payload location.
     */
    private hasDraft(payload: ReviewCommentDraftPayload): boolean {
        const location = this.buildDraftLocation(payload);
        return location ? this.reviewCommentFacade.hasDraft(location) : false;
    }

    /**
     * Ensures a draft entry exists for the payload location.
     */
    private ensureDraft(payload: ReviewCommentDraftPayload): void {
        const location = this.buildDraftLocation(payload);
        if (location) {
            this.reviewCommentFacade.ensureDraft(location);
        }
    }

    /**
     * Returns draft text for the payload location.
     */
    private getDraftText(payload: ReviewCommentDraftPayload): string {
        const location = this.buildDraftLocation(payload);
        return location ? this.reviewCommentFacade.getDraftText(location) : '';
    }

    /**
     * Sets draft text for the payload location.
     */
    private setDraftText(payload: ReviewCommentDraftPayload, text: string): void {
        const location = this.buildDraftLocation(payload);
        if (location) {
            this.reviewCommentFacade.setDraftText(location, text);
        }
    }

    /**
     * Removes draft text for the payload location.
     */
    private cancelDraft(payload: ReviewCommentDraftPayload): void {
        const location = this.buildDraftLocation(payload);
        if (location) {
            this.reviewCommentFacade.removeDraft(location);
        }
    }

    /**
     * Checks whether create-thread is pending for the payload location.
     */
    private isDraftSubmitting(payload: ReviewCommentDraftPayload): boolean {
        const location = this.buildDraftLocation(payload);
        return location ? this.reviewCommentFacade.isDraftSubmitting(location) : false;
    }

    /**
     * Parses a draft key back into file/line information.
     *
     * @param key The internal draft key.
     * @returns Parsed file and zero-based line.
     */
    private parseDraftKey(key: string): { fileName: string; line: number } {
        const separatorIndex = key.lastIndexOf(':');
        if (separatorIndex < 0) {
            return { fileName: key, line: 0 };
        }
        const fileName = key.substring(0, separatorIndex);
        const line = Number(key.substring(separatorIndex + 1));
        return { fileName, line: Number.isFinite(line) ? line : 0 };
    }

    /**
     * Builds the Monaco widget id for a thread widget.
     *
     * @param threadId The thread id.
     * @returns The widget id used for Monaco line widgets.
     */
    private buildThreadWidgetId(threadId: number): string {
        return `review-comment-thread-${threadId}`;
    }

    /**
     * Builds the Monaco widget id for a draft widget.
     *
     * @param fileName The file name used for keying.
     * @param line The zero-based line index.
     * @returns The widget id used for Monaco line widgets.
     */
    private buildDraftWidgetId(fileName: string, line: number): string {
        return `review-comment-${fileName}-${line}`;
    }

    /**
     * Builds the internal map key for a draft widget.
     *
     * @param fileName The file name used for keying.
     * @param line The zero-based line index.
     * @returns The internal key for draft widgets.
     */
    private getDraftKey(fileName: string, line: number): string {
        return `${fileName}:${line}`;
    }
}

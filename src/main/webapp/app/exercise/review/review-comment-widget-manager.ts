import { ComponentRef, OutputRefSubscription, ViewContainerRef } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ReviewCommentDraftWidgetComponent } from 'app/exercise/review/review-comment-draft-widget/review-comment-draft-widget.component';
import { ReviewCommentThreadWidgetComponent } from 'app/exercise/review/review-comment-thread-widget/review-comment-thread-widget.component';
import { CommentThread, CommentThreadLocationType, ReviewThreadLocation } from 'app/exercise/shared/entities/review/comment-thread.model';
import { InlineCodeChange } from 'app/exercise/shared/entities/review/comment-content.model';

export type ReviewCommentDraftContext = {
    targetType: CommentThreadLocationType;
    filePath?: string;
    auxiliaryRepositoryId?: number;
};

export type ReviewCommentWidgetManagerConfig = {
    hoverButtonClass: string;
    shouldShowHoverButton: () => boolean;
    canSubmit: () => boolean;
    getDraftFileName: () => string | undefined;
    getDraftContext: (payload: { lineNumber: number; fileName: string }) => ReviewCommentDraftContext | undefined;
    getThreads: () => CommentThread[];
    filterThread: (thread: CommentThread) => boolean;
    getThreadLine: (thread: CommentThread) => number;
    onAdd: (payload: { lineNumber: number; fileName: string }) => void;
    onApplyInlineFix?: (payload: { thread: CommentThread; inlineFix: InlineCodeChange }) => void;
    onNavigateToLocation?: (location: ReviewThreadLocation) => void;
    showLocationWarning: () => boolean;
    showFeedbackAction: (thread: CommentThread) => boolean;
};

enum InlineFixApplyResult {
    APPLIED,
    OUTDATED,
    INVALID,
}

export class ReviewCommentWidgetManager {
    private readonly draftLinesByFile: Map<string, Set<number>> = new Map();
    private readonly draftWidgetRefs: Map<string, ComponentRef<ReviewCommentDraftWidgetComponent>> = new Map();
    private readonly draftWidgetSubscriptions: Map<string, OutputRefSubscription[]> = new Map();
    private readonly threadWidgetRefs: Map<number, ComponentRef<ReviewCommentThreadWidgetComponent>> = new Map();
    private readonly collapseState: Map<number, boolean> = new Map();

    constructor(
        private readonly editor: MonacoEditorComponent,
        private readonly viewContainerRef: ViewContainerRef,
        private readonly config: ReviewCommentWidgetManagerConfig,
    ) {
        this.editor.getEditor().onDidScrollChange(() => this.hideAllThreadMenus());
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
        const canSubmit = this.config.canSubmit();
        this.draftWidgetRefs.forEach((ref) => {
            ref.setInput('canSubmit', canSubmit);
        });
    }

    /**
     * Updates thread inputs in-place when widgets already exist.
     * Returns false when at least one thread widget is missing, so callers can fall back to a full render.
     *
     * @param threads The latest thread data to apply to widgets.
     * @returns True if all widgets were updated, false if any were missing.
     */
    tryUpdateThreadInputs(threads: CommentThread[]): boolean {
        let updated = true;
        for (const thread of threads) {
            const widgetRef = this.threadWidgetRefs.get(thread.id);
            if (!widgetRef) {
                updated = false;
                continue;
            }
            this.setThreadWidgetInputs(widgetRef, thread, this.config.showLocationWarning());
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
    }

    /**
     * Removes draft widgets and clears draft line tracking.
     */
    clearDrafts(): void {
        for (const [fileName, lines] of this.draftLinesByFile.entries()) {
            for (const line of lines) {
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
        const draftContext = this.resolveDraftContext(fileName, lineNumberZeroBased);
        if (!draftContext) {
            return;
        }
        const existing = this.draftLinesByFile.get(fileName) ?? new Set<number>();
        if (!existing.has(lineNumberZeroBased)) {
            existing.add(lineNumberZeroBased);
            this.draftLinesByFile.set(fileName, existing);
        }
        const widgetKey = this.getDraftKey(fileName, lineNumberZeroBased);
        const widgetRef = this.getOrCreateDraftWidget(widgetKey, fileName, lineNumberZeroBased);
        this.setDraftWidgetInputs(widgetRef, lineNumberZeroBased, draftContext);
        this.renderDraftLineWidget(fileName, lineNumberZeroBased, widgetRef);
        this.config.onAdd({ lineNumber, fileName });
    }

    /**
     * Renders draft widgets for the active file based on tracked draft lines.
     */
    private addDraftWidgets(): void {
        const activeFileName = this.config.getDraftFileName();
        if (!activeFileName) {
            return;
        }
        const lines = this.draftLinesByFile.get(activeFileName);
        if (!lines) {
            return;
        }
        for (const line of [...lines]) {
            const draftContext = this.resolveDraftContext(activeFileName, line);
            if (!draftContext) {
                this.removeDraft(activeFileName, line);
                continue;
            }
            const widgetRef = this.getOrCreateDraftWidget(this.getDraftKey(activeFileName, line), activeFileName, line);
            this.setDraftWidgetInputs(widgetRef, line, draftContext);
            this.renderDraftLineWidget(activeFileName, line, widgetRef);
        }
    }

    /**
     * Returns an existing draft widget or creates one with its output subscriptions.
     *
     * @param widgetKey The internal key for the draft widget.
     * @param fileName The file where the draft is shown.
     * @param line The zero-based line index of the draft.
     * @returns The component reference for the draft widget.
     */
    private getOrCreateDraftWidget(widgetKey: string, fileName: string, line: number): ComponentRef<ReviewCommentDraftWidgetComponent> {
        const existingWidgetRef = this.draftWidgetRefs.get(widgetKey);
        if (existingWidgetRef) {
            return existingWidgetRef;
        }

        const widgetRef = this.viewContainerRef.createComponent(ReviewCommentDraftWidgetComponent);
        this.registerDraftWidgetSubscriptions(widgetKey, widgetRef, fileName, line);
        this.draftWidgetRefs.set(widgetKey, widgetRef);
        return widgetRef;
    }

    /**
     * Replaces the Monaco line widget for the current draft location.
     *
     * @param fileName The file where the draft is shown.
     * @param line The zero-based line index of the draft.
     * @param widgetRef The component reference for the draft widget.
     */
    private renderDraftLineWidget(fileName: string, line: number, widgetRef: ComponentRef<ReviewCommentDraftWidgetComponent>): void {
        const widgetId = this.buildDraftWidgetId(fileName, line);
        this.editor.disposeWidgetsByPrefix(widgetId);
        this.editor.addLineWidget(line + 1, widgetId, widgetRef.location.nativeElement);
    }

    /**
     * Renders thread widgets, disposing stale ones and preserving collapse state.
     */
    private addSavedWidgets(): void {
        const threads = this.config.getThreads().filter((thread) => this.config.filterThread(thread));
        this.disposeStaleThreadWidgets(new Set(threads.map((thread) => thread.id)));
        for (const thread of threads) {
            this.renderThreadWidget(thread);
        }
    }

    /**
     * Disposes widgets for threads that are no longer visible in the active editor context.
     *
     * @param activeThreadIds Thread ids that should remain rendered.
     */
    private disposeStaleThreadWidgets(activeThreadIds: Set<number>): void {
        for (const [threadId, ref] of this.threadWidgetRefs.entries()) {
            if (!activeThreadIds.has(threadId)) {
                this.editor.disposeWidgetsByPrefix(this.buildThreadWidgetId(threadId));
                ref.destroy();
                this.threadWidgetRefs.delete(threadId);
            }
        }
    }

    /**
     * Creates or updates a thread widget and places it at the current mapped line.
     *
     * @param thread The review thread to render.
     */
    private renderThreadWidget(thread: CommentThread): void {
        const showLocationWarning = this.config.showLocationWarning();
        const widgetRef = this.getOrCreateThreadWidget(thread, showLocationWarning);
        this.setThreadWidgetInputs(widgetRef, thread, showLocationWarning);
        this.renderThreadLineWidget(thread, widgetRef);
    }

    /**
     * Returns an existing thread widget or creates one with its output subscriptions.
     *
     * @param thread The review thread associated with the widget.
     * @param showLocationWarning Whether the widget should start collapsed because locations may be stale.
     * @returns The component reference for the thread widget.
     */
    private getOrCreateThreadWidget(thread: CommentThread, showLocationWarning: boolean): ComponentRef<ReviewCommentThreadWidgetComponent> {
        const existingWidgetRef = this.threadWidgetRefs.get(thread.id);
        if (existingWidgetRef) {
            return existingWidgetRef;
        }

        const widgetRef = this.viewContainerRef.createComponent(ReviewCommentThreadWidgetComponent);
        this.initializeThreadCollapseState(thread, showLocationWarning);
        widgetRef.setInput('initialCollapsed', this.collapseState.get(thread.id) ?? false);
        this.registerThreadWidgetSubscriptions(thread, widgetRef);
        this.threadWidgetRefs.set(thread.id, widgetRef);
        return widgetRef;
    }

    /**
     * Initializes the persisted collapse state for a newly created thread widget.
     *
     * @param thread The review thread associated with the widget.
     * @param showLocationWarning Whether the widget should start collapsed because locations may be stale.
     */
    private initializeThreadCollapseState(thread: CommentThread, showLocationWarning: boolean): void {
        if (!this.collapseState.has(thread.id)) {
            this.collapseState.set(thread.id, thread.resolved || showLocationWarning);
        }
    }

    /**
     * Applies the current thread data to a thread widget.
     *
     * @param widgetRef The component reference for the thread widget.
     * @param thread The latest review thread data.
     * @param showLocationWarning Whether the widget should show a location warning.
     */
    private setThreadWidgetInputs(widgetRef: ComponentRef<ReviewCommentThreadWidgetComponent>, thread: CommentThread, showLocationWarning: boolean): void {
        widgetRef.setInput('thread', thread);
        widgetRef.setInput('showLocationWarning', showLocationWarning);
        widgetRef.setInput('showFeedbackAction', this.config.showFeedbackAction(thread));
    }

    /**
     * Registers output handlers for a newly created thread widget.
     *
     * @param thread The review thread associated with the widget.
     * @param widgetRef The component reference for the thread widget.
     */
    private registerThreadWidgetSubscriptions(thread: CommentThread, widgetRef: ComponentRef<ReviewCommentThreadWidgetComponent>): void {
        widgetRef.instance.onToggleCollapse.subscribe((collapsed) => this.collapseState.set(thread.id, collapsed));
        widgetRef.instance.onNavigateToLocation.subscribe((location) => this.config.onNavigateToLocation?.(location));
        widgetRef.instance.onApplyInlineFix.subscribe((inlineFix) => this.handleInlineFixApplication(thread, widgetRef, inlineFix));
    }

    /**
     * Applies an inline fix and notifies the owning editor only when the edit was actually written.
     *
     * @param thread The thread that owns the inline fix.
     * @param widgetRef The widget that triggered the action.
     * @param inlineFix The inline fix payload to apply.
     */
    private handleInlineFixApplication(thread: CommentThread, widgetRef: ComponentRef<ReviewCommentThreadWidgetComponent>, inlineFix: InlineCodeChange): void {
        const applyResult = this.applyInlineFixToActiveEditor(inlineFix);
        widgetRef.instance.setInlineFixOutdatedWarning(applyResult === InlineFixApplyResult.OUTDATED);
        if (applyResult === InlineFixApplyResult.APPLIED) {
            const currentThread = this.config.getThreads().find((candidate) => candidate.id === thread.id) ?? thread;
            this.config.onApplyInlineFix?.({ thread: currentThread, inlineFix });
        }
    }

    /**
     * Replaces the Monaco line widget for the current thread location.
     *
     * @param thread The review thread to render.
     * @param widgetRef The component reference for the thread widget.
     */
    private renderThreadLineWidget(thread: CommentThread, widgetRef: ComponentRef<ReviewCommentThreadWidgetComponent>): void {
        const widgetId = this.buildThreadWidgetId(thread.id);
        this.editor.disposeWidgetsByPrefix(widgetId);
        this.editor.addLineWidget(this.config.getThreadLine(thread) + 1, widgetId, widgetRef.location.nativeElement);
    }

    /**
     * Removes the draft widget and tracking entry for a file/line pair.
     *
     * @param fileName The file where the draft was created.
     * @param line The zero-based line index of the draft.
     */
    private removeDraft(fileName: string, line: number): void {
        const existing = this.draftLinesByFile.get(fileName);
        if (existing) {
            existing.delete(line);
            if (existing.size === 0) {
                this.draftLinesByFile.delete(fileName);
            }
        }
        const widgetKey = this.getDraftKey(fileName, line);
        const subscriptions = this.draftWidgetSubscriptions.get(widgetKey);
        subscriptions?.forEach((subscription) => subscription.unsubscribe());
        this.draftWidgetSubscriptions.delete(widgetKey);
        this.editor.disposeWidgetsByPrefix(this.buildDraftWidgetId(fileName, line));
        this.draftWidgetRefs.get(widgetKey)?.destroy();
        this.draftWidgetRefs.delete(widgetKey);
    }

    /**
     * Disposes all draft widget component refs.
     */
    private disposeDraftWidgets(): void {
        this.draftWidgetSubscriptions.forEach((subscriptions) => {
            subscriptions.forEach((subscription) => subscription.unsubscribe());
        });
        this.draftWidgetSubscriptions.clear();
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
        return `review-comment-${fileName}::${line}::`;
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

    /**
     * Registers draft-widget output subscriptions so they can be cleaned up explicitly.
     *
     * @param widgetKey The internal key for the draft widget.
     * @param widgetRef The draft widget component reference.
     * @param fileName The file name where the draft is shown.
     * @param line The zero-based line index of the draft.
     */
    private registerDraftWidgetSubscriptions(widgetKey: string, widgetRef: ComponentRef<ReviewCommentDraftWidgetComponent>, fileName: string, line: number): void {
        const submittedSubscription = widgetRef.instance.onSubmitted.subscribe(() => this.removeDraft(fileName, line));
        const cancelSubscription = widgetRef.instance.onCancel.subscribe(() => this.removeDraft(fileName, line));
        this.draftWidgetSubscriptions.set(widgetKey, [submittedSubscription, cancelSubscription]);
    }

    /**
     * Resolves draft context for a file/line pair.
     *
     * @param fileName The file where the draft is shown.
     * @param line The zero-based line index.
     * @returns Draft context for creating a thread, if available.
     */
    private resolveDraftContext(fileName: string, line: number): ReviewCommentDraftContext | undefined {
        return this.config.getDraftContext({ lineNumber: line + 1, fileName });
    }

    /**
     * Applies draft-widget inputs required to create a thread in the active exercise context.
     *
     * @param widgetRef The draft widget component reference.
     * @param line The zero-based line index.
     * @param draftContext The resolved draft context for thread creation.
     */
    private setDraftWidgetInputs(widgetRef: ComponentRef<ReviewCommentDraftWidgetComponent>, line: number, draftContext: ReviewCommentDraftContext): void {
        widgetRef.setInput('canSubmit', this.config.canSubmit());
        widgetRef.setInput('targetType', draftContext.targetType);
        widgetRef.setInput('lineNumber', line + 1);
        widgetRef.setInput('filePath', draftContext.filePath);
        widgetRef.setInput('auxiliaryRepositoryId', draftContext.auxiliaryRepositoryId);
    }

    /**
     * Hides all currently open thread menus so detached overlays cannot drift while the editor scrolls.
     */
    private hideAllThreadMenus(): void {
        for (const ref of this.threadWidgetRefs.values()) {
            ref.instance.hideAllCommentMenus();
        }
    }

    /**
     * Applies an inline fix only if the current editor content in the referenced line range
     * still matches the expected snapshot from consistency-check creation time.
     *
     * @param inlineFix The inline fix payload to apply.
     * @returns Whether the fix was applied, is outdated, or could not be applied because the editor state is invalid.
     */
    private applyInlineFixToActiveEditor(inlineFix: InlineCodeChange): InlineFixApplyResult {
        const model = this.editor.getModel();
        if (!model) {
            return InlineFixApplyResult.INVALID;
        }

        const startLine = inlineFix.startLine;
        const endLine = inlineFix.endLine;
        if (startLine < 1 || endLine < startLine || endLine > model.getLineCount()) {
            return InlineFixApplyResult.INVALID;
        }

        const endColumn = model.getLineMaxColumn(endLine);
        const currentCode = model.getValueInRange({
            startLineNumber: startLine,
            startColumn: 1,
            endLineNumber: endLine,
            endColumn,
        });
        if (currentCode !== inlineFix.expectedCode) {
            return InlineFixApplyResult.OUTDATED;
        }

        this.editor.getActiveEditor().executeEdits('ReviewCommentWidgetManager::applyInlineFix', [
            {
                range: {
                    startLineNumber: startLine,
                    startColumn: 1,
                    endLineNumber: endLine,
                    endColumn,
                },
                text: inlineFix.replacementCode,
                forceMoveMarkers: true,
            },
        ]);
        return InlineFixApplyResult.APPLIED;
    }
}

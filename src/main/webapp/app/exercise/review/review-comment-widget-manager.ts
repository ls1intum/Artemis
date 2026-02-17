import { ComponentRef, ViewContainerRef } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ReviewCommentDraftWidgetComponent } from 'app/exercise/review/review-comment-draft-widget/review-comment-draft-widget.component';
import { ReviewCommentThreadWidgetComponent } from 'app/exercise/review/review-comment-thread-widget/review-comment-thread-widget.component';
import { CommentThread, CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';

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
    showLocationWarning: () => boolean;
};

export class ReviewCommentWidgetManager {
    private readonly draftLinesByFile: Map<string, Set<number>> = new Map();
    private readonly draftWidgetRefs: Map<string, ComponentRef<ReviewCommentDraftWidgetComponent>> = new Map();
    private readonly threadWidgetRefs: Map<number, ComponentRef<ReviewCommentThreadWidgetComponent>> = new Map();
    private readonly collapseState: Map<number, boolean> = new Map();

    constructor(
        private readonly editor: MonacoEditorComponent,
        private readonly viewContainerRef: ViewContainerRef,
        private readonly config: ReviewCommentWidgetManagerConfig,
    ) {}

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
     *
     * @param threads The latest thread data to apply to widgets.
     * @returns True if all widgets were updated, false if any were missing.
     */
    updateThreadInputs(threads: CommentThread[]): boolean {
        let updated = true;
        for (const thread of threads) {
            const widgetRef = this.threadWidgetRefs.get(thread.id);
            if (!widgetRef) {
                updated = false;
                continue;
            }
            widgetRef.setInput('thread', thread);
            widgetRef.setInput('showLocationWarning', this.config.showLocationWarning());
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
        let widgetRef = this.draftWidgetRefs.get(widgetKey);
        if (!widgetRef) {
            widgetRef = this.viewContainerRef.createComponent(ReviewCommentDraftWidgetComponent);
            widgetRef.instance.onSubmitted.subscribe(() => this.removeDraft(fileName, lineNumberZeroBased));
            widgetRef.instance.onCancel.subscribe(() => this.removeDraft(fileName, lineNumberZeroBased));
            this.draftWidgetRefs.set(widgetKey, widgetRef);
        }
        this.setDraftWidgetInputs(widgetRef, lineNumberZeroBased, draftContext);
        // Re-adding the same draft line must replace the existing Monaco widget to avoid stacked view zones.
        this.editor.disposeWidgetsByPrefix(this.buildDraftWidgetId(fileName, lineNumberZeroBased));
        this.editor.addLineWidget(lineNumber, this.buildDraftWidgetId(fileName, lineNumberZeroBased), widgetRef.location.nativeElement);
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
            const widgetKey = this.getDraftKey(activeFileName, line);
            let widgetRef = this.draftWidgetRefs.get(widgetKey);
            if (!widgetRef) {
                widgetRef = this.viewContainerRef.createComponent(ReviewCommentDraftWidgetComponent);
                widgetRef.instance.onSubmitted.subscribe(() => this.removeDraft(activeFileName, line));
                widgetRef.instance.onCancel.subscribe(() => this.removeDraft(activeFileName, line));
                this.draftWidgetRefs.set(widgetKey, widgetRef);
            }
            this.setDraftWidgetInputs(widgetRef, line, draftContext);
            // Keep rendering idempotent when renderWidgets() runs repeatedly for the same draft.
            this.editor.disposeWidgetsByPrefix(this.buildDraftWidgetId(activeFileName, line));
            this.editor.addLineWidget(line + 1, this.buildDraftWidgetId(activeFileName, line), widgetRef.location.nativeElement);
        }
    }

    /**
     * Renders thread widgets, disposing stale ones and preserving collapse state.
     */
    private addSavedWidgets(): void {
        const threads = this.config.getThreads().filter((thread) => this.config.filterThread(thread));
        const threadIds = new Set<number>();
        for (const thread of threads) {
            threadIds.add(thread.id);
        }
        for (const [threadId, ref] of this.threadWidgetRefs.entries()) {
            if (!threadIds.has(threadId)) {
                this.editor.disposeWidgetsByPrefix(this.buildThreadWidgetId(threadId));
                ref.destroy();
                this.threadWidgetRefs.delete(threadId);
            }
        }
        for (const thread of threads) {
            const line = this.config.getThreadLine(thread);
            const widgetId = this.buildThreadWidgetId(thread.id);
            const showLocationWarning = this.config.showLocationWarning();
            let widgetRef = this.threadWidgetRefs.get(thread.id);
            if (!widgetRef) {
                widgetRef = this.viewContainerRef.createComponent(ReviewCommentThreadWidgetComponent);
                widgetRef.setInput('thread', thread);
                widgetRef.setInput('showLocationWarning', showLocationWarning);
                if (!this.collapseState.has(thread.id)) {
                    const shouldCollapse = thread.resolved || showLocationWarning;
                    this.collapseState.set(thread.id, shouldCollapse);
                }
                widgetRef.setInput('initialCollapsed', this.collapseState.get(thread.id) ?? false);
                widgetRef.instance.onToggleCollapse.subscribe((collapsed) => this.collapseState.set(thread.id, collapsed));
                this.threadWidgetRefs.set(thread.id, widgetRef);
            } else {
                widgetRef.setInput('thread', thread);
                widgetRef.setInput('showLocationWarning', showLocationWarning);
            }
            this.editor.disposeWidgetsByPrefix(widgetId);
            this.editor.addLineWidget(line + 1, widgetId, widgetRef.location.nativeElement);
        }
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
}

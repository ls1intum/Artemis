import { ComponentRef, ViewContainerRef } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ReviewCommentDraftWidgetComponent } from 'app/exercise/review/review-comment-draft-widget/review-comment-draft-widget.component';
import { ReviewCommentThreadWidgetComponent } from 'app/exercise/review/review-comment-thread-widget/review-comment-thread-widget.component';
import { CommentThread } from 'app/exercise/shared/entities/review/comment-thread.model';

export type ReviewCommentWidgetManagerConfig = {
    hoverButtonClass: string;
    shouldShowHoverButton: () => boolean;
    canSubmit?: () => boolean;
    getDraftFileName: () => string | undefined;
    getThreads: () => CommentThread[];
    filterThread: (thread: CommentThread) => boolean;
    getThreadLine: (thread: CommentThread) => number;
    onAdd?: (payload: { lineNumber: number; fileName: string }) => void;
    onSubmit: (payload: { lineNumber: number; fileName: string; text: string }) => void;
    onDelete: (commentId: number) => void;
    onReply: (payload: { threadId: number; text: string }) => void;
    onUpdate: (payload: { commentId: number; text: string }) => void;
    onToggleResolved: (payload: { threadId: number; resolved: boolean }) => void;
    requestRender: () => void;
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

    updateHoverButton(): void {
        if (this.config.shouldShowHoverButton()) {
            this.editor.setLineDecorationsHoverButton(this.config.hoverButtonClass, (lineNumber) => this.addDraft(lineNumber));
        } else {
            this.editor.clearLineDecorationsHoverButton();
        }
    }

    renderWidgets(): void {
        this.addSavedWidgets();
        this.addDraftWidgets();
    }

    updateDraftInputs(): void {
        const canSubmit = this.config.canSubmit ? this.config.canSubmit() : true;
        this.draftWidgetRefs.forEach((ref) => ref.setInput('canSubmit', canSubmit));
    }

    updateThreadInputs(threads: CommentThread[]): boolean {
        let updated = true;
        for (const thread of threads) {
            if (thread.id === undefined || thread.id === null) {
                continue;
            }
            const widgetRef = this.threadWidgetRefs.get(thread.id);
            if (!widgetRef) {
                updated = false;
                continue;
            }
            widgetRef.setInput('thread', thread);
        }
        return updated;
    }

    disposeAll(): void {
        this.disposeDraftWidgets();
        this.disposeSavedWidgets();
        this.draftLinesByFile.clear();
        this.collapseState.clear();
    }

    clearDrafts(): void {
        for (const [fileName, lines] of this.draftLinesByFile.entries()) {
            for (const line of lines) {
                this.editor.disposeWidgetsByPrefix(this.buildDraftWidgetId(fileName, line));
            }
        }
        this.disposeDraftWidgets();
        this.draftLinesByFile.clear();
    }

    private addDraft(lineNumber: number): void {
        const fileName = this.config.getDraftFileName();
        if (!fileName) {
            return;
        }
        const lineNumberZeroBased = lineNumber - 1;
        const existing = this.draftLinesByFile.get(fileName) ?? new Set<number>();
        if (!existing.has(lineNumberZeroBased)) {
            existing.add(lineNumberZeroBased);
            this.draftLinesByFile.set(fileName, existing);
        }
        const widgetKey = this.getDraftKey(fileName, lineNumberZeroBased);
        let widgetRef = this.draftWidgetRefs.get(widgetKey);
        if (!widgetRef) {
            widgetRef = this.viewContainerRef.createComponent(ReviewCommentDraftWidgetComponent);
            widgetRef.instance.onSubmit.subscribe((text) => this.submitDraft(fileName, lineNumberZeroBased, text));
            widgetRef.instance.onCancel.subscribe(() => this.removeDraft(fileName, lineNumberZeroBased));
            this.draftWidgetRefs.set(widgetKey, widgetRef);
        }
        widgetRef.setInput('canSubmit', this.config.canSubmit ? this.config.canSubmit() : true);
        this.editor.addLineWidget(lineNumber, this.buildDraftWidgetId(fileName, lineNumberZeroBased), widgetRef.location.nativeElement);
        this.config.onAdd?.({ lineNumber, fileName });
    }

    private addDraftWidgets(): void {
        const activeFileName = this.config.getDraftFileName();
        if (!activeFileName) {
            return;
        }
        const lines = this.draftLinesByFile.get(activeFileName);
        if (!lines) {
            return;
        }
        for (const line of lines) {
            const widgetKey = this.getDraftKey(activeFileName, line);
            let widgetRef = this.draftWidgetRefs.get(widgetKey);
            if (!widgetRef) {
                widgetRef = this.viewContainerRef.createComponent(ReviewCommentDraftWidgetComponent);
                widgetRef.instance.onSubmit.subscribe((text) => this.submitDraft(activeFileName, line, text));
                widgetRef.instance.onCancel.subscribe(() => this.removeDraft(activeFileName, line));
                this.draftWidgetRefs.set(widgetKey, widgetRef);
            }
            widgetRef.setInput('canSubmit', this.config.canSubmit ? this.config.canSubmit() : true);
            this.editor.addLineWidget(line + 1, this.buildDraftWidgetId(activeFileName, line), widgetRef.location.nativeElement);
        }
    }

    private addSavedWidgets(): void {
        const threads = this.config.getThreads().filter((thread) => this.config.filterThread(thread));
        const threadIds = new Set<number>();
        for (const thread of threads) {
            if (thread.id !== undefined && thread.id !== null) {
                threadIds.add(thread.id);
            }
        }
        for (const [threadId, ref] of this.threadWidgetRefs.entries()) {
            if (!threadIds.has(threadId)) {
                this.editor.disposeWidgetsByPrefix(this.buildThreadWidgetId(threadId));
                ref.destroy();
                this.threadWidgetRefs.delete(threadId);
            }
        }
        for (const thread of threads) {
            if (thread.id === undefined || thread.id === null) {
                continue;
            }
            const line = this.config.getThreadLine(thread);
            const widgetId = this.buildThreadWidgetId(thread.id);
            let widgetRef = this.threadWidgetRefs.get(thread.id);
            if (!widgetRef) {
                widgetRef = this.viewContainerRef.createComponent(ReviewCommentThreadWidgetComponent);
                widgetRef.setInput('thread', thread);
                if (!this.collapseState.has(thread.id)) {
                    this.collapseState.set(thread.id, thread.resolved);
                }
                widgetRef.setInput('initialCollapsed', this.collapseState.get(thread.id) ?? false);
                widgetRef.instance.onDelete.subscribe((commentId) => this.config.onDelete(commentId));
                widgetRef.instance.onReply.subscribe((text) => this.config.onReply({ threadId: thread.id, text }));
                widgetRef.instance.onUpdate.subscribe((event) => this.config.onUpdate(event));
                widgetRef.instance.onToggleResolved.subscribe((resolved) => this.config.onToggleResolved({ threadId: thread.id, resolved }));
                widgetRef.instance.onToggleCollapse.subscribe((collapsed) => this.collapseState.set(thread.id, collapsed));
                this.threadWidgetRefs.set(thread.id, widgetRef);
            } else {
                widgetRef.setInput('thread', thread);
            }
            this.editor.disposeWidgetsByPrefix(widgetId);
            this.editor.addLineWidget(line + 1, widgetId, widgetRef.location.nativeElement);
        }
    }

    private submitDraft(fileName: string, line: number, text: string): void {
        this.config.onSubmit({ lineNumber: line + 1, fileName, text });
        this.removeDraft(fileName, line);
    }

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

    private disposeDraftWidgets(): void {
        this.draftWidgetRefs.forEach((ref) => ref.destroy());
        this.draftWidgetRefs.clear();
    }

    private disposeSavedWidgets(): void {
        this.threadWidgetRefs.forEach((ref) => ref.destroy());
        this.threadWidgetRefs.clear();
    }

    private buildThreadWidgetId(threadId: number): string {
        return `review-comment-thread-${threadId}`;
    }

    private buildDraftWidgetId(fileName: string, line: number): string {
        return `review-comment-${fileName}-${line}`;
    }

    private getDraftKey(fileName: string, line: number): string {
        return `${fileName}:${line}`;
    }
}

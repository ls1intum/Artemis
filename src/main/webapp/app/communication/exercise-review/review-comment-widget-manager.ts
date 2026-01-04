import { ComponentRef, ViewContainerRef } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ReviewCommentDraftWidgetComponent } from 'app/communication/exercise-review/review-comment-draft-widget.component';
import { ReviewCommentThreadWidgetComponent } from 'app/communication/exercise-review/review-comment-thread-widget.component';
import { CommentThread } from 'app/communication/shared/entities/exercise-review/comment-thread.model';

export type ReviewCommentWidgetManagerConfig = {
    hoverButtonClass: string;
    shouldShowHoverButton: () => boolean;
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

    disposeAll(): void {
        this.disposeDraftWidgets();
        this.disposeSavedWidgets();
        this.draftLinesByFile.clear();
        this.collapseState.clear();
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
            this.config.requestRender();
        }
        this.config.onAdd?.({ lineNumber, fileName });
    }

    private addDraftWidgets(): void {
        for (const [fileName, lines] of this.draftLinesByFile.entries()) {
            for (const line of lines) {
                const widgetKey = this.getDraftKey(fileName, line);
                let widgetRef = this.draftWidgetRefs.get(widgetKey);
                if (!widgetRef) {
                    widgetRef = this.viewContainerRef.createComponent(ReviewCommentDraftWidgetComponent);
                    widgetRef.instance.onSubmit.subscribe((text) => this.submitDraft(fileName, line, text));
                    widgetRef.instance.onCancel.subscribe(() => this.removeDraft(fileName, line));
                    this.draftWidgetRefs.set(widgetKey, widgetRef);
                }
                this.editor.addLineWidget(line + 1, `review-comment-${fileName}-${line}`, widgetRef.location.nativeElement);
            }
        }
    }

    private addSavedWidgets(): void {
        this.disposeSavedWidgets();
        const threads = this.config.getThreads().filter((thread) => this.config.filterThread(thread));
        for (const thread of threads) {
            const line = this.config.getThreadLine(thread);
            const widgetRef = this.viewContainerRef.createComponent(ReviewCommentThreadWidgetComponent);
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
            this.editor.addLineWidget(line + 1, `review-comment-thread-${thread.id}`, widgetRef.location.nativeElement);
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
        this.draftWidgetRefs.get(widgetKey)?.destroy();
        this.draftWidgetRefs.delete(widgetKey);
        this.config.requestRender();
    }

    private disposeDraftWidgets(): void {
        this.draftWidgetRefs.forEach((ref) => ref.destroy());
        this.draftWidgetRefs.clear();
    }

    private disposeSavedWidgets(): void {
        this.threadWidgetRefs.forEach((ref) => ref.destroy());
        this.threadWidgetRefs.clear();
    }

    private getDraftKey(fileName: string, line: number): string {
        return `${fileName}:${line}`;
    }
}

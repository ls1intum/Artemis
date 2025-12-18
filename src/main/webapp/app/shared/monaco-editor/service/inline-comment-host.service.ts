import { ApplicationRef, ComponentRef, EnvironmentInjector, Injectable, createComponent, inject } from '@angular/core';
import { InlineCommentWidgetComponent } from '../inline-comment-widget/inline-comment-widget.component';
import { InlineComment } from '../model/inline-comment.model';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

/**
 * Information about an active inline comment widget.
 */
export interface ActiveWidgetInfo {
    id: string;
    startLine: number;
    endLine: number;
    componentRef: ComponentRef<InlineCommentWidgetComponent>;
}

/**
 * Service to manage inline comment widgets in Monaco editor.
 * Handles creating, destroying, and tracking active widgets.
 */
@Injectable({
    providedIn: 'root',
})
export class InlineCommentHostService {
    private appRef = inject(ApplicationRef);
    private injector = inject(EnvironmentInjector);

    private activeWidgets = new Map<string, ActiveWidgetInfo>();
    private widgetCounter = 0;

    /**
     * Opens an inline comment widget at the specified line.
     * @param editor The markdown editor to add the widget to.
     * @param startLine The start line of the comment.
     * @param endLine The end line of the comment.
     * @param existingComment Optional existing comment to edit.
     * @param callbacks Event callbacks.
     * @returns The widget ID.
     */
    openWidget(
        editor: MarkdownEditorMonacoComponent,
        startLine: number,
        endLine: number,
        existingComment: InlineComment | undefined,
        callbacks: {
            onSave: (comment: InlineComment) => void;
            onApply: (comment: InlineComment) => void;
            onCancel: () => void;
            onCancelApply?: () => void;
            onDelete?: (commentId: string) => void;
        },
        options?: {
            collapsed?: boolean;
            readOnly?: boolean;
            globalApplying?: boolean;
        },
    ): string {
        const widgetId = `inline-comment-widget-${++this.widgetCounter}`;

        // Create the Angular component dynamically
        const componentRef = createComponent(InlineCommentWidgetComponent, {
            environmentInjector: this.injector,
        });

        // Set inputs
        componentRef.setInput('startLine', startLine);
        componentRef.setInput('endLine', endLine);
        componentRef.setInput('collapsed', options?.collapsed ?? false);
        componentRef.setInput('readOnly', options?.readOnly ?? false);
        componentRef.setInput('globalApplying', options?.globalApplying ?? false);
        if (existingComment) {
            componentRef.setInput('existingComment', existingComment);
        }

        // Subscribe to outputs
        componentRef.instance.onSave.subscribe((comment: InlineComment) => {
            callbacks.onSave(comment);
            // Don't close widget - let user continue editing or click Apply later
        });

        componentRef.instance.onApply.subscribe((comment: InlineComment) => {
            callbacks.onApply(comment);
        });

        componentRef.instance.onCancel.subscribe(() => {
            callbacks.onCancel();
            this.closeWidget(widgetId, editor);
        });

        componentRef.instance.onCancelApply.subscribe(() => {
            callbacks.onCancelApply?.();
        });

        componentRef.instance.onDelete.subscribe((commentId: string) => {
            callbacks.onDelete?.(commentId);
            this.closeWidget(widgetId, editor);
        });

        // Attach to Angular's change detection
        this.appRef.attachView(componentRef.hostView);

        // Get the DOM element
        const domElement = componentRef.location.nativeElement as HTMLElement;

        // Add as Monaco line widget (after the end line)
        editor.addLineWidget(endLine, widgetId, domElement);

        // Track the widget
        this.activeWidgets.set(widgetId, {
            id: widgetId,
            startLine,
            endLine,
            componentRef,
        });

        // Focus the textarea after a short delay
        setTimeout(() => {
            const textarea = domElement.querySelector('textarea');
            textarea?.focus();
        }, 100);

        return widgetId;
    }

    /**
     * Closes an inline comment widget.
     * @param widgetId The ID of the widget to close.
     * @param editor The editor containing the widget.
     */
    closeWidget(widgetId: string, editor: MarkdownEditorMonacoComponent): void {
        const widgetInfo = this.activeWidgets.get(widgetId);
        if (!widgetInfo) {
            return;
        }

        // Remove from Monaco
        editor.removeLineWidget(widgetId);

        // Destroy Angular component
        this.appRef.detachView(widgetInfo.componentRef.hostView);
        widgetInfo.componentRef.destroy();

        // Remove from tracking
        this.activeWidgets.delete(widgetId);
    }

    /**
     * Closes all active inline comment widgets.
     * @param editor The editor containing the widgets.
     */
    closeAllWidgets(editor: MarkdownEditorMonacoComponent): void {
        for (const widgetId of this.activeWidgets.keys()) {
            this.closeWidget(widgetId, editor);
        }
    }

    /**
     * Forcefully clears all tracked widgets. Use when editor reference may be stale.
     * Destroys Angular components but cannot remove from Monaco (editor may be destroyed).
     */
    clearAllWidgets(): void {
        for (const widgetInfo of this.activeWidgets.values()) {
            this.appRef.detachView(widgetInfo.componentRef.hostView);
            widgetInfo.componentRef.destroy();
        }
        this.activeWidgets.clear();
    }

    /**
     * Checks if there's an active widget at the given line.
     */
    hasWidgetAtLine(lineNumber: number): boolean {
        for (const widget of this.activeWidgets.values()) {
            if (lineNumber >= widget.startLine && lineNumber <= widget.endLine) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the number of active widgets.
     */
    getActiveWidgetCount(): number {
        return this.activeWidgets.size;
    }

    /**
     * Updates the globalApplying state on all active widgets.
     * This should be called when any AI operation starts or stops.
     * @param isApplying Whether any AI operation is currently in progress.
     */
    updateGlobalApplyingState(isApplying: boolean): void {
        for (const widgetInfo of this.activeWidgets.values()) {
            widgetInfo.componentRef.setInput('globalApplying', isApplying);
        }
    }
}

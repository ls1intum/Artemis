import * as monaco from 'monaco-editor';

export interface LineSelectionCallbacks {
    /** Called when user clicks the "+" button to add a comment */
    onAddComment: (startLine: number, endLine: number) => void;
}

/**
 * Options for the line selection handler.
 */
export interface LineSelectionOptions {
    /** Localized tooltip text for the add button (e.g., "Add inline comment") */
    addButtonTooltip: string;
}

export interface LineSelectionDisposable {
    dispose: () => void;
}

/**
 * Sets up line selection handling for Monaco editor.
 * Shows a "+" gutter decoration when lines are selected, allowing users to add inline comments.
 *
 * @param editor The Monaco editor instance
 * @param callbacks Callbacks for user actions
 * @param options Options including localized strings
 * @returns Disposable to clean up listeners. Callers must call dispose() (e.g., in ngOnDestroy) to prevent memory leaks.
 */
export function setupLineSelectionHandler(editor: monaco.editor.IStandaloneCodeEditor, callbacks: LineSelectionCallbacks, options: LineSelectionOptions): LineSelectionDisposable {
    const disposables: monaco.IDisposable[] = [];
    let currentDecorations: string[] = [];
    let addButtonOverlay: HTMLElement | undefined;
    let currentSelection: { startLine: number; endLine: number } | undefined;

    // Gutter decoration style
    const gutterDecorationClass = 'inline-comment-add-gutter';

    /**
     * Updates the gutter decoration based on current selection.
     */
    function updateGutterDecoration(): void {
        const selection = editor.getSelection();

        if (!selection || selection.isEmpty()) {
            // No selection - clear decorations
            currentDecorations = editor.deltaDecorations(currentDecorations, []);
            removeAddButton();
            currentSelection = undefined;
            return;
        }

        const startLine = selection.startLineNumber;
        const endLine = selection.endLineNumber;
        currentSelection = { startLine, endLine };

        // Add gutter decoration for selected lines
        currentDecorations = editor.deltaDecorations(currentDecorations, [
            {
                range: new monaco.Range(startLine, 1, endLine, 1),
                options: {
                    isWholeLine: true,
                    linesDecorationsClassName: gutterDecorationClass,
                    glyphMarginClassName: 'inline-comment-glyph-margin',
                },
            },
        ]);

        // Show add button in the gutter
        showAddButton(startLine, endLine);
    }

    /**
     * Shows the "+" button in the gutter area.
     * Creates the button only once if not already existing.
     */
    function showAddButton(startLine: number, endLine: number): void {
        const domNode = editor.getDomNode();
        if (!domNode) {
            return;
        }

        // Create the button only if it doesn't exist
        if (!addButtonOverlay) {
            addButtonOverlay = document.createElement('div');
            addButtonOverlay.className = 'inline-comment-add-button';
            addButtonOverlay.textContent = '+';
            addButtonOverlay.title = options.addButtonTooltip;

            const lineHeight = editor.getOption(monaco.editor.EditorOption.lineHeight);

            addButtonOverlay.style.position = 'absolute';
            addButtonOverlay.style.left = '4px';
            addButtonOverlay.style.width = '20px';
            addButtonOverlay.style.height = `${lineHeight}px`;
            addButtonOverlay.style.lineHeight = `${lineHeight}px`;
            addButtonOverlay.style.cursor = 'pointer';
            addButtonOverlay.style.zIndex = '10';
            addButtonOverlay.style.textAlign = 'center';
            addButtonOverlay.style.fontWeight = 'bold';
            addButtonOverlay.style.fontSize = '14px';
            addButtonOverlay.style.color = 'var(--bs-primary)';
            addButtonOverlay.style.backgroundColor = 'var(--bs-tertiary-bg)';
            addButtonOverlay.style.borderRadius = '4px';
            addButtonOverlay.style.border = '1px solid var(--bs-border-color)';

            addButtonOverlay.addEventListener('click', (e) => {
                e.stopPropagation();
                if (currentSelection) {
                    callbacks.onAddComment(currentSelection.startLine, currentSelection.endLine);
                }
            });

            // Append to editor's parent for proper positioning
            const overlayContainer = domNode.querySelector('.margin') || domNode;
            if (overlayContainer && overlayContainer instanceof HTMLElement) {
                overlayContainer.style.position = 'relative';
                overlayContainer.appendChild(addButtonOverlay);
            }
        }

        // Update position
        repositionAddButton(startLine);
    }

    /**
     * Repositions the add button based on current scroll position.
     */
    function repositionAddButton(startLine: number): void {
        if (!addButtonOverlay) {
            return;
        }

        const lineTop = editor.getTopForLineNumber(startLine);
        const scrollTop = editor.getScrollTop();
        addButtonOverlay.style.top = `${lineTop - scrollTop}px`;
    }

    /**
     * Removes the add button from the DOM.
     */
    function removeAddButton(): void {
        if (addButtonOverlay && addButtonOverlay.parentNode) {
            addButtonOverlay.parentNode.removeChild(addButtonOverlay);
        }
        addButtonOverlay = undefined;
    }

    // Listen for selection changes
    const selectionListener = editor.onDidChangeCursorSelection(() => {
        updateGutterDecoration();
    });
    disposables.push(selectionListener);

    // Listen for scroll changes to reposition the button
    const scrollListener = editor.onDidScrollChange(() => {
        if (currentSelection && addButtonOverlay) {
            repositionAddButton(currentSelection.startLine);
        }
    });
    disposables.push(scrollListener);

    // Listen for editor blur to clear decorations
    const blurListener = editor.onDidBlurEditorText(() => {
        // Small delay to allow click on the add button
        setTimeout(() => {
            if (!editor.hasTextFocus()) {
                currentDecorations = editor.deltaDecorations(currentDecorations, []);
                removeAddButton();
                currentSelection = undefined;
            }
        }, 200);
    });
    disposables.push(blurListener);

    return {
        dispose: () => {
            disposables.forEach((d) => d.dispose());
            currentDecorations = editor.deltaDecorations(currentDecorations, []);
            removeAddButton();
            currentSelection = undefined;
        },
    };
}

/**
 * CSS styles to be injected for the line selection handler.
 * Add this to a global stylesheet or component styles.
 */
export const LINE_SELECTION_HANDLER_STYLES = `
.inline-comment-add-gutter {
    background-color: rgba(var(--bs-primary-rgb), 0.1);
}

.inline-comment-glyph-margin {
    background-color: rgba(var(--bs-primary-rgb), 0.2);
}

.inline-comment-add-button:hover {
    background-color: var(--bs-primary) !important;
    color: white !important;
}
`;

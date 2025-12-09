import * as monaco from 'monaco-editor';

export interface LineSelectionCallbacks {
    /** Called when user clicks the "+" button to add a comment */
    onAddComment: (startLine: number, endLine: number) => void;
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
 * @returns Disposable to clean up listeners
 */
export function setupLineSelectionHandler(editor: monaco.editor.IStandaloneCodeEditor, callbacks: LineSelectionCallbacks): LineSelectionDisposable {
    const disposables: monaco.IDisposable[] = [];
    let currentDecorations: string[] = [];
    let addButtonOverlay: HTMLElement | undefined;

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
            return;
        }

        const startLine = selection.startLineNumber;
        const endLine = selection.endLineNumber;

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
     */
    function showAddButton(startLine: number, endLine: number): void {
        removeAddButton();

        const domNode = editor.getDomNode();
        if (!domNode) {
            return;
        }

        // Create the add button
        addButtonOverlay = document.createElement('div');
        addButtonOverlay.className = 'inline-comment-add-button';
        addButtonOverlay.innerHTML = '+';
        addButtonOverlay.title = 'Add inline comment';

        // Position the button at the start line
        const lineTop = editor.getTopForLineNumber(startLine);
        const scrollTop = editor.getScrollTop();
        const lineHeight = editor.getOption(monaco.editor.EditorOption.lineHeight);

        addButtonOverlay.style.position = 'absolute';
        addButtonOverlay.style.left = '4px';
        addButtonOverlay.style.top = `${lineTop - scrollTop}px`;
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
            callbacks.onAddComment(startLine, endLine);
        });

        // Append to editor's parent for proper positioning
        const overlayContainer = domNode.querySelector('.margin') || domNode;
        if (overlayContainer && overlayContainer instanceof HTMLElement) {
            overlayContainer.style.position = 'relative';
            overlayContainer.appendChild(addButtonOverlay);
        }
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
        const selection = editor.getSelection();
        if (selection && !selection.isEmpty()) {
            showAddButton(selection.startLineNumber, selection.endLineNumber);
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
            }
        }, 200);
    });
    disposables.push(blurListener);

    return {
        dispose: () => {
            disposables.forEach((d) => d.dispose());
            currentDecorations = editor.deltaDecorations(currentDecorations, []);
            removeAddButton();
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

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
     * Creates and styles the add button element.
     */
    function createAddButton(lineHeight: number): HTMLElement {
        const button = document.createElement('div');
        button.className = 'inline-comment-add-button';
        button.textContent = '+';
        button.title = options.addButtonTooltip;

        Object.assign(button.style, {
            position: 'absolute',
            left: '4px',
            width: '20px',
            height: `${lineHeight}px`,
            lineHeight: `${lineHeight}px`,
            cursor: 'pointer',
            zIndex: '10',
            textAlign: 'center',
            fontWeight: 'bold',
            fontSize: '14px',
            color: 'var(--bs-primary)',
            backgroundColor: 'var(--bs-tertiary-bg)',
            borderRadius: '4px',
            border: '1px solid var(--bs-border-color)',
        });

        button.addEventListener('click', (e) => {
            e.stopPropagation();
            if (currentSelection) {
                callbacks.onAddComment(currentSelection.startLine, currentSelection.endLine);
            }
        });

        return button;
    }

    /**
     * Shows the "+" button in the gutter area.
     */
    function showAddButton(startLine: number, endLine: number): void {
        const domNode = editor.getDomNode();
        if (!domNode) return;

        if (!addButtonOverlay) {
            addButtonOverlay = createAddButton(editor.getOption(monaco.editor.EditorOption.lineHeight));

            const overlayContainer = domNode.querySelector('.margin') || domNode;
            if (overlayContainer instanceof HTMLElement) {
                overlayContainer.style.position = 'relative';
                overlayContainer.appendChild(addButtonOverlay);
            }
        }

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

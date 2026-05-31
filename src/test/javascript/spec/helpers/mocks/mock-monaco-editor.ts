/**
 * Mock for the `monaco-editor` module in Vitest.
 *
 * Wired in via a path alias in `vitest.config.ts` so that the (heavy, worker-based, ESM) real Monaco package
 * never loads under jsdom. The mock is intentionally a small but *stateful* in-memory editor: text round-trips
 * through {@link MockTextModel}, line counting / line content / ranges work, and cursor position, selection,
 * commands and edit operations behave well enough to exercise component logic (text changes, emoji conversion,
 * grapheme-aware backspace, model switching, read-only handling, diff-mode wiring).
 *
 * What it deliberately does NOT do is render Monaco's view layer (line-number gutter, decoration/overlay DOM,
 * minimap, ...). Tests that previously asserted on that rendered DOM assert on component state / the Monaco API
 * calls instead.
 */

// ---------------------------------------------------------------------------
// Editor option ids (mirroring monaco.editor.EditorOption). getOption(id) maps the id back to a stored option.
// ---------------------------------------------------------------------------
export const EditorOption = {
    automaticLayout: 19,
    cursorBlinking: 32,
    cursorStyle: 34,
    folding: 44,
    fontFamily: 58,
    fontSize: 61,
    lineDecorationsWidth: 73,
    lineHeight: 75,
    lineNumbers: 76,
    minimap: 81,
    readOnly: 104,
    renderLineHighlight: 110,
    scrollBeyondLastLine: 119,
    wordWrap: 149,
};

const OPTION_ID_TO_NAME = new Map<number, string>(Object.entries(EditorOption).map(([name, id]) => [id, name]));

const DEFAULT_OPTION_VALUES: Record<string, unknown> = {
    automaticLayout: true,
    fontSize: 14,
    lineHeight: 19,
    readOnly: false,
    wordWrap: 'off',
    folding: true,
    lineDecorationsWidth: 10,
};

// ---------------------------------------------------------------------------
// Disposable helper
// ---------------------------------------------------------------------------
const noopDisposable = () => ({ dispose: () => {} });

type Listener<T> = (event: T) => void;

class ListenerSet<T> {
    private readonly listeners = new Set<Listener<T>>();
    add(listener: Listener<T>) {
        this.listeners.add(listener);
        return { dispose: () => this.listeners.delete(listener) };
    }
    fire(event: T) {
        // Copy to a array first so listeners that dispose themselves don't mutate the set mid-iteration.
        [...this.listeners].forEach((listener) => listener(event));
    }
}

// ---------------------------------------------------------------------------
// Position / Range / Selection
// ---------------------------------------------------------------------------
export class Position {
    constructor(
        public lineNumber: number,
        public column: number,
    ) {}
}

export class Range {
    constructor(
        public startLineNumber: number,
        public startColumn: number,
        public endLineNumber: number,
        public endColumn: number,
    ) {}
}

export class Selection extends Range {
    get positionLineNumber(): number {
        return this.endLineNumber;
    }
    get positionColumn(): number {
        return this.endColumn;
    }
    isEmpty(): boolean {
        return this.startLineNumber === this.endLineNumber && this.startColumn === this.endColumn;
    }
}

// ---------------------------------------------------------------------------
// Stateful text model
// ---------------------------------------------------------------------------
class MockTextModel {
    private value: string;
    private language: string;
    private disposed = false;
    private cursorStateCallback?: (selections: Selection[]) => void;
    readonly uri?: { toString: () => string; path: string };
    readonly onContentChanged = new ListenerSet<void>();

    constructor(content = '', language = 'plaintext', uri?: { toString: () => string; path?: string }) {
        this.value = content ?? '';
        this.language = language ?? 'plaintext';
        if (uri) {
            this.uri = { toString: uri.toString, path: uri.path ?? uri.toString().replace(/^[a-z]+:\/\//i, '/') };
        }
    }

    getValue(): string {
        return this.value;
    }

    setValue(value: string): void {
        this.value = value ?? '';
        this.onContentChanged.fire();
    }

    getLineCount(): number {
        return this.value.split('\n').length;
    }

    getLineContent(lineNumber: number): string {
        const lines = this.value.split('\n');
        if (lineNumber < 1 || lineNumber > lines.length) {
            throw new Error(`Illegal line number: ${lineNumber}`);
        }
        return lines[lineNumber - 1];
    }

    getValueInRange(range: { startLineNumber: number; startColumn: number; endLineNumber: number; endColumn: number }): string {
        const lines = this.value.split('\n');
        const startLine = Math.max(1, range.startLineNumber);
        const endLine = Math.min(lines.length, range.endLineNumber);
        if (startLine > endLine) {
            return '';
        }
        if (startLine === endLine) {
            return (lines[startLine - 1] ?? '').substring(range.startColumn - 1, range.endColumn - 1);
        }
        const collected: string[] = [(lines[startLine - 1] ?? '').substring(range.startColumn - 1)];
        for (let line = startLine; line < endLine - 1; line++) {
            collected.push(lines[line]);
        }
        collected.push((lines[endLine - 1] ?? '').substring(0, range.endColumn - 1));
        return collected.join('\n');
    }

    getFullModelRange(): Range {
        const lines = this.value.split('\n');
        const lastLine = lines.length;
        return new Range(1, 1, lastLine, (lines[lastLine - 1]?.length ?? 0) + 1);
    }

    getLanguageId(): string {
        return this.language;
    }

    setLanguage(language: string): void {
        this.language = language;
    }

    setEOL(): void {}

    updateOptions(): void {}

    onDidChangeContent(listener: () => void) {
        return this.onContentChanged.add(listener);
    }

    /**
     * Applies the given edit operations to the in-memory text and optionally derives the new cursor state.
     * Only the subset used by the editor component (single targeted replacements) is supported.
     */
    setCursorStateCallback(callback: (selections: Selection[]) => void): void {
        this.cursorStateCallback = callback;
    }

    pushEditOperations(_base: unknown, operations: { range: Range; text: string }[], cursorStateComputer?: (inverse: unknown[]) => Selection[] | null): Selection[] | null {
        for (const operation of operations) {
            this.applyEdit(operation.range, operation.text ?? '');
        }
        this.onContentChanged.fire();
        const selections = cursorStateComputer ? cursorStateComputer([]) : null;
        if (selections && selections.length) {
            this.cursorStateCallback?.(selections);
        }
        return selections;
    }

    private applyEdit(range: { startLineNumber: number; startColumn: number; endLineNumber: number; endColumn: number }, text: string): void {
        const lines = this.value.split('\n');
        const startLineIndex = range.startLineNumber - 1;
        const endLineIndex = range.endLineNumber - 1;
        const before = (lines[startLineIndex] ?? '').substring(0, range.startColumn - 1);
        const after = (lines[endLineIndex] ?? '').substring(range.endColumn - 1);
        const replacement = (before + text + after).split('\n');
        lines.splice(startLineIndex, endLineIndex - startLineIndex + 1, ...replacement);
        this.value = lines.join('\n');
    }

    dispose(): void {
        this.disposed = true;
    }

    isDisposed(): boolean {
        return this.disposed;
    }
}

// ---------------------------------------------------------------------------
// Stateful standalone code editor
// ---------------------------------------------------------------------------
let editorIdCounter = 0;
let commandIdCounter = 0;

const createMockEditor = (options?: { value?: string }) => {
    const editorId = `mock-editor-${++editorIdCounter}`;
    let model: MockTextModel | null = new MockTextModel(options?.value ?? '');
    let position = new Position(1, 1);
    let selection = new Selection(1, 1, 1, 1);
    const optionValues: Record<string, unknown> = { ...DEFAULT_OPTION_VALUES };
    const commands = new Map<string, () => void>();

    const modelContentListeners = new ListenerSet<{ changes: unknown[] }>();
    const cursorSelectionListeners = new ListenerSet<{ selection: Selection }>();
    const cursorPositionListeners = new ListenerSet<{ position: Position }>();

    const fireContentChanged = () => modelContentListeners.fire({ changes: [] });
    // Re-emit a model's content change through the editor-level listeners.
    let modelContentSubscription = model?.onDidChangeContent(fireContentChanged);

    const isReadOnly = () => optionValues['readOnly'] === true;

    const setEditorPosition = (newPosition: Position) => {
        position = newPosition;
        selection = new Selection(newPosition.lineNumber, newPosition.column, newPosition.lineNumber, newPosition.column);
    };

    // Let model edit operations (e.g. the grapheme-aware backspace command) move the editor cursor, mirroring how
    // Monaco applies a pushEditOperations cursor-state computer to the editor.
    const wireCursorCallback = (target: MockTextModel | null) => {
        target?.setCursorStateCallback((selections) => {
            const sel = selections[0];
            if (sel) {
                setEditorPosition(new Position(sel.positionLineNumber, sel.positionColumn));
            }
        });
    };
    wireCursorCallback(model);

    return {
        getId: () => editorId,
        getModel: () => model,
        setModel: (newModel: MockTextModel | null) => {
            modelContentSubscription?.dispose();
            model = newModel;
            modelContentSubscription = model?.onDidChangeContent(fireContentChanged);
            wireCursorCallback(model);
        },
        getValue: () => model?.getValue() ?? '',
        setValue: (value: string) => model?.setValue(value),

        getPosition: () => position,
        setPosition: (newPosition: Position) => setEditorPosition(new Position(newPosition.lineNumber, newPosition.column)),
        getSelection: () => selection,
        setSelection: (range: { startLineNumber: number; startColumn: number; endLineNumber: number; endColumn: number }) => {
            selection = new Selection(range.startLineNumber, range.startColumn, range.endLineNumber, range.endColumn);
            position = new Position(range.endLineNumber, range.endColumn);
            cursorSelectionListeners.fire({ selection });
        },

        getOption: (optionId: number) => {
            const name = OPTION_ID_TO_NAME.get(optionId);
            return name !== undefined ? optionValues[name] : 0;
        },
        updateOptions: (newOptions: Record<string, unknown>) => {
            Object.assign(optionValues, newOptions);
        },

        addCommand: (_keybinding: number, handler: () => void) => {
            const commandId = `mock-command-${++commandIdCounter}`;
            commands.set(commandId, handler);
            return commandId;
        },
        addAction: () => ({ dispose: () => {} }),
        executeEdits: () => true,
        trigger: (_source: string, handlerId: string, payload?: { text?: string }) => {
            const command = commands.get(handlerId);
            if (command) {
                command();
                return;
            }
            if (isReadOnly()) {
                return;
            }
            if (handlerId === 'type' && payload?.text != null) {
                model?.setValue((model?.getValue() ?? '') + payload.text);
            } else if (handlerId === 'deleteLeft') {
                const value = model?.getValue() ?? '';
                model?.setValue(value.slice(0, -1));
            }
        },

        // Listeners
        onDidChangeModelContent: (listener: Listener<{ changes: unknown[] }>) => modelContentListeners.add(listener),
        onDidChangeCursorSelection: (listener: Listener<{ selection: Selection }>) => cursorSelectionListeners.add(listener),
        onDidChangeCursorPosition: (listener: Listener<{ position: Position }>) => cursorPositionListeners.add(listener),
        onDidContentSizeChange: noopDisposable,
        onDidChangeHiddenAreas: noopDisposable,
        onDidFocusEditorText: noopDisposable,
        onDidBlurEditorText: noopDisposable,
        onDidBlurEditorWidget: noopDisposable,
        onDidScrollChange: noopDisposable,
        onDidLayoutChange: noopDisposable,
        onKeyDown: noopDisposable,
        onKeyUp: noopDisposable,
        onDidPaste: noopDisposable,
        onMouseDown: noopDisposable,
        onMouseMove: noopDisposable,
        onMouseLeave: noopDisposable,

        // Layout / DOM
        focus: () => {},
        layout: () => {},
        revealLine: () => {},
        revealLineNearTop: () => {},
        revealLineInCenter: () => {},
        revealRangeInCenter: () => {},
        getContentHeight: () => 100,
        getContentWidth: () => 500,
        getScrollHeight: () => 100,
        getScrollWidth: () => 500,
        getScrollTop: () => 0,
        getScrollLeft: () => 0,
        setScrollTop: () => {},
        setScrollPosition: () => {},
        getScrolledVisiblePosition: () => ({ top: 0, left: 0, height: 18 }),
        getContainerDomNode: () => document.createElement('div'),
        getDomNode: () => document.createElement('div'),
        getContribution: () => null,

        // Decorations / widgets / view zones. The element models manipulate their own DOM nodes, so the editor only
        // needs to (a) not throw and (b) attach glyph-margin widget nodes to the document so getElementById works.
        createDecorationsCollection: (_initial?: unknown[]) => ({
            append: () => {},
            clear: () => {},
            set: () => {},
            getRanges: () => [],
            length: 0,
        }),
        deltaDecorations: () => [],
        addGlyphMarginWidget: (widget: { getDomNode: () => HTMLElement }) => document.body.appendChild(widget.getDomNode()),
        removeGlyphMarginWidget: (widget: { getDomNode: () => HTMLElement }) => widget.getDomNode().remove(),
        addOverlayWidget: () => {},
        removeOverlayWidget: () => {},
        layoutOverlayWidget: () => {},
        addContentWidget: () => {},
        removeContentWidget: () => {},
        layoutContentWidget: () => {},
        changeViewZones: (callback: (accessor: { addZone: () => string; removeZone: () => void; layoutZone: () => void }) => void) => {
            callback({ addZone: () => `mock-zone-${++commandIdCounter}`, removeZone: () => {}, layoutZone: () => {} });
        },

        dispose: () => {
            modelContentSubscription?.dispose();
        },
    };
};

// ---------------------------------------------------------------------------
// Minimal stateful diff editor (used by the service / diff-editor specs; the monaco-editor component spec
// overrides the factory with its own mock). setModel wires the original/modified models to the inner editors so
// getText round-trips, and onDidUpdateDiff fires whenever the model changes so the readiness flow can be exercised.
// Real diff computation is not emulated; getLineChanges defaults to [] and specs override it when they assert counts.
// ---------------------------------------------------------------------------
const createMockDiffEditor = () => {
    const originalEditor = createMockEditor();
    const modifiedEditor = createMockEditor();
    let containerDomNode: HTMLElement = document.createElement('div');
    let diffUpdateListener: (() => void) | undefined;
    const diffEditorId = `mock-diff-editor-${++editorIdCounter}`;
    const editor = {
        getId: () => diffEditorId,
        dispose: () => {},
        updateOptions: () => {},
        layout: () => {},
        setModel: (model: { original: MockTextModel; modified: MockTextModel } | null) => {
            if (model) {
                originalEditor.setModel(model.original);
                modifiedEditor.setModel(model.modified);
            }
            diffUpdateListener?.();
        },
        getModel: () => null,
        getOriginalEditor: () => originalEditor,
        getModifiedEditor: () => modifiedEditor,
        onDidUpdateDiff: (listener: () => void) => {
            diffUpdateListener = listener;
            return { dispose: () => (diffUpdateListener = undefined) };
        },
        onDidChangeHiddenAreas: noopDisposable,
        getLineChanges: (): unknown[] => [],
        getContainerDomNode: () => containerDomNode,
        setContainerDomNode: (node: HTMLElement) => (containerDomNode = node),
    };
    return editor;
};

// ---------------------------------------------------------------------------
// Model registry (uri -> model) for getModel/createModel round-trips.
// ---------------------------------------------------------------------------
const modelCache = new Map<string, MockTextModel>();

/**
 * Clears the model cache to prevent test pollution. Call this in beforeEach hooks to reset state between tests.
 */
export function clearModelCache(): void {
    modelCache.clear();
}

// Inserts a classed child node into the host element (mirroring how real Monaco renders into the element) so
// specs can assert the editor was mounted, and makes getContainerDomNode return the host element.
const mountInto = (domElement: HTMLElement | undefined, className: string): HTMLElement | undefined => {
    if (!domElement) {
        return undefined;
    }
    const child = document.createElement('div');
    child.className = className;
    domElement.appendChild(child);
    return domElement;
};

export const editor = {
    create: (domElement?: HTMLElement, options?: { value?: string }) => {
        const mockEditor = createMockEditor(options);
        const host = mountInto(domElement, 'monaco-editor');
        if (host) {
            mockEditor.getContainerDomNode = () => host;
        }
        return mockEditor;
    },
    createDiffEditor: (domElement?: HTMLElement) => {
        const mockDiffEditor = createMockDiffEditor();
        const host = mountInto(domElement, 'monaco-diff-editor');
        if (host) {
            mockDiffEditor.setContainerDomNode(host);
        }
        return mockDiffEditor;
    },

    createModel: (content?: string, language?: string, uri?: { toString: () => string; path?: string }) => {
        const model = new MockTextModel(content ?? '', language, uri);
        if (uri) {
            modelCache.set(uri.toString(), model);
        }
        return model;
    },

    getModel: (uri: { toString: () => string }) => modelCache.get(uri.toString()) ?? null,

    setModelLanguage: (model: MockTextModel, language: string) => model?.setLanguage?.(language),

    defineTheme: () => {},
    setTheme: () => {},

    EndOfLineSequence: { LF: 0, CRLF: 1 },
    EndOfLinePreference: { TextDefined: 0, LF: 1, CRLF: 2 },
    GlyphMarginLane: { Left: 1, Center: 2, Right: 3 },
    TrackedRangeStickiness: { AlwaysGrowsWhenTypingAtEdges: 0, NeverGrowsWhenTypingAtEdges: 1, GrowsOnlyWhenTypingBefore: 2, GrowsOnlyWhenTypingAfter: 3 },
    ScrollType: { Smooth: 0, Immediate: 1 },

    EditorOption,
};

const registeredLanguages: { id: string }[] = [];

export const languages = {
    register: (language: { id: string }) => {
        registeredLanguages.push(language);
    },
    getLanguages: () => registeredLanguages,
    setMonarchTokensProvider: () => {},
    setLanguageConfiguration: () => {},
    registerCompletionItemProvider: () => ({ dispose: () => {} }),
};

// KeyCode values matching monaco-editor for MonacoTextEditorAdapter compatibility
export const KeyCode = {
    Backspace: 1,
    Tab: 2,
    Enter: 3,
    Escape: 9,
    Delete: 10,
    KeyA: 31,
    KeyB: 32,
    KeyC: 33,
    KeyD: 34,
    KeyE: 35,
    KeyF: 36,
    KeyG: 37,
    KeyH: 38,
    KeyI: 39,
    KeyJ: 40,
    KeyK: 41,
    KeyL: 42,
    KeyM: 43,
    KeyN: 44,
    KeyO: 45,
    KeyP: 46,
    KeyQ: 47,
    KeyR: 48,
    KeyS: 49,
    KeyT: 50,
    KeyU: 51,
    KeyV: 52,
    KeyW: 53,
    KeyX: 54,
    KeyY: 55,
    KeyZ: 56,
};

export const KeyMod = { CtrlCmd: 2048, Shift: 1024, Alt: 512, WinCtrl: 256 };
export const MarkerSeverity = { Error: 8, Warning: 4, Info: 2, Hint: 1 };
export const Uri = {
    parse: (value: string) => ({ toString: () => value, path: value.replace(/^[a-z]+:\/\//i, '/') }),
};

export default { editor, languages, Range, Position, Selection, KeyCode, KeyMod, MarkerSeverity, Uri };

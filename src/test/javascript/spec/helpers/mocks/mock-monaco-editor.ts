/**
 * Mock for monaco-editor module resolution in Vitest.
 * Used via path alias in vitest.config.ts to prevent ESM resolution errors.
 */
const createMockModel = () => ({
    dispose: () => {},
    getValue: () => '',
    setValue: () => {},
    setEOL: () => {},
    getLineCount: () => 1,
    getLineContent: () => '',
    getValueInRange: () => '',
    getFullModelRange: () => ({
        startLineNumber: 1,
        startColumn: 1,
        endLineNumber: 1,
        endColumn: 1,
        getEndPosition: () => ({ lineNumber: 1, column: 1 }),
    }),
    updateOptions: () => {},
    onDidChangeContent: () => ({ dispose: () => {} }),
});

let editorIdCounter = 0;

const createMockEditor = () => {
    const editorId = `mock-editor-${++editorIdCounter}`;
    return {
        dispose: () => {},
        getValue: () => '',
        setValue: () => {},
        getModel: () => createMockModel(),
        setModel: () => {},
        onDidChangeCursorPosition: () => ({ dispose: () => {} }),
        onDidChangeCursorSelection: () => ({ dispose: () => {} }),
        onDidChangeModelContent: () => ({ dispose: () => {} }),
        onDidFocusEditorText: () => ({ dispose: () => {} }),
        onDidBlurEditorText: () => ({ dispose: () => {} }),
        onDidBlurEditorWidget: () => ({ dispose: () => {} }),
        onKeyDown: () => ({ dispose: () => {} }),
        onKeyUp: () => ({ dispose: () => {} }),
        focus: () => {},
        layout: () => {},
        getPosition: () => ({ lineNumber: 1, column: 1 }),
        setPosition: () => {},
        getSelection: () => ({ startLineNumber: 1, startColumn: 1, endLineNumber: 1, endColumn: 1 }),
        setSelection: () => {},
        executeEdits: () => true,
        addAction: () => ({ dispose: () => {} }),
        addCommand: () => null,
        getContainerDomNode: () => document.createElement('div'),
        getDomNode: () => document.createElement('div'),
        updateOptions: () => {},
        revealLine: () => {},
        revealLineInCenter: () => {},
        revealRangeInCenter: () => {},
        onDidPaste: () => ({ dispose: () => {} }),
        trigger: () => {},
        deltaDecorations: () => [],
        getContribution: () => null,
        createDecorationsCollection: () => ({ clear: () => {}, set: () => {} }),
        onDidContentSizeChange: () => ({ dispose: () => {} }),
        getContentHeight: () => 100,
        getContentWidth: () => 500,
        getScrollHeight: () => 100,
        getScrollWidth: () => 500,
        getScrollTop: () => 0,
        getScrollLeft: () => 0,
        setScrollTop: () => {},
        setScrollPosition: () => {},
        onDidScrollChange: () => ({ dispose: () => {} }),
        getId: () => editorId,
        getOption: (optionId: number) => {
            const optionValues: Record<number, unknown> = {
                19: true, // automaticLayout
                61: 14, // fontSize
                75: 19, // lineHeight
                104: false, // readOnly
                149: 'off', // wordWrap
            };
            return optionValues[optionId] ?? 0;
        },
    };
};

// Model cache for getModel/createModel
const modelCache = new Map<string, ReturnType<typeof createMockModel>>();

/**
 * Clears the model cache to prevent test pollution.
 * Call this in beforeEach hooks to reset state between tests.
 */
export function clearModelCache(): void {
    modelCache.clear();
}

export const editor = {
    create: createMockEditor,

    createModel: (content?: string, language?: string, uri?: { toString: () => string }) => {
        const model = createMockModel();
        // content/language are intentionally ignored in this lightweight mock
        if (uri) {
            modelCache.set(uri.toString(), model);
        }
        return model;
    },

    getModel: (uri: { toString: () => string }) => modelCache.get(uri.toString()) ?? null,

    // Some codebases call this directly when switching languages in tests
    setModelLanguage: () => {},

    defineTheme: () => {},
    setTheme: () => {},

    EndOfLineSequence: { LF: 0, CRLF: 1 },
    EndOfLinePreference: { TextDefined: 0, LF: 1, CRLF: 2 },

    EditorOption: {
        lineHeight: 75,
        readOnly: 104,
        fontSize: 61,
        fontFamily: 58,
        wordWrap: 149,
        minimap: 81,
        scrollBeyondLastLine: 119,
        lineNumbers: 76,
        renderLineHighlight: 110,
        cursorStyle: 34,
        cursorBlinking: 32,
        automaticLayout: 19,
    },
};

export const languages = {
    register: () => {},
    setMonarchTokensProvider: () => {},
    setLanguageConfiguration: () => {},
    registerCompletionItemProvider: () => ({ dispose: () => {} }),
};

export class Range {
    constructor(
        public startLineNumber: number,
        public startColumn: number,
        public endLineNumber: number,
        public endColumn: number,
    ) {}
}

export class Position {
    constructor(
        public lineNumber: number,
        public column: number,
    ) {}
}

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
export const Uri = { parse: (s: string) => ({ toString: () => s }) };

export default { editor, languages, Range, Position, KeyCode, KeyMod, MarkerSeverity, Uri };

/**
 * Mock for monaco-editor module resolution in Vitest.
 * Used via path alias in vitest.config.ts to prevent ESM resolution errors.
 */
export const editor = {
    create: () => ({}),
    createModel: () => ({}),
    defineTheme: () => {},
    setTheme: () => {},
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

export enum TextEditorKeyCode {
    KeyB,
    KeyI,
    KeyU,
}

export enum TextEditorKeyModifier {
    /**
     * The Ctrl key on Windows and Linux, and the Cmd key on macOS.
     */
    CtrlCmd,
}

export class TextEditorKeybinding {
    constructor(
        private readonly key: TextEditorKeyCode,
        private readonly modifier: TextEditorKeyModifier,
    ) {}

    getKey(): TextEditorKeyCode {
        return this.key;
    }

    getModifier(): TextEditorKeyModifier {
        return this.modifier;
    }
}

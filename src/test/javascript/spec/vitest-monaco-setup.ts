/**
 * Extra jsdom polyfills required by the REAL `monaco-editor` package used in the Monaco integration specs
 * (see vitest.monaco.config.ts). The standard Vitest setup runs first; this only adds browser APIs that
 * jsdom omits but Monaco's standalone services rely on.
 */

import * as monaco from 'monaco-editor';

// Monaco's theme/icon stylesheet service calls CSS.escape, which jsdom does not implement.
const cssGlobal = (globalThis as unknown as { CSS?: { escape?: (value: string) => string } }).CSS ?? {};
if (typeof cssGlobal.escape !== 'function') {
    // Minimal CSS.escape: backslash-escape every character that is not a safe identifier character.
    // Sufficient for Monaco's generated icon/theme class names.
    cssGlobal.escape = (value: string) => String(value).replace(/[^a-zA-Z0-9_-]/g, (ch) => `\\${ch}`);
}
(globalThis as unknown as { CSS: { escape: (value: string) => string } }).CSS = cssGlobal as { escape: (value: string) => string };

// The communication actions fire-and-forget `editor.trigger(..., 'editor.action.triggerSuggest', {})` to open the
// suggest widget. The real `monaco-editor` standalone build only registers that command once the suggest controller
// contribution is loaded, which it is not in the headless jsdom integration setup. Without it the trigger returns a
// rejected promise that surfaces as an unhandled rejection and fails the run, even though the specs never assert on
// the suggest widget itself. Register a no-op so the trigger resolves; behaviour under test is unchanged.
const TRIGGER_SUGGEST_COMMAND_ID = 'editor.action.triggerSuggest';
const commandsRegistry = (monaco.editor as unknown as { registerCommand?: (id: string, handler: () => void) => void }).registerCommand;
if (typeof commandsRegistry === 'function') {
    commandsRegistry(TRIGGER_SUGGEST_COMMAND_ID, () => {});
}

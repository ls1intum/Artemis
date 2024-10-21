import { faLink } from '@fortawesome/free-solid-svg-icons';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { TranslateService } from '@ngx-translate/core';
import { Disposable } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { urlRegex } from 'app/shared/link-preview/services/linkify.service';

interface UrlArguments {
    text: string;
    url: string;
}

/**
 * Action to insert a URL into the editor. They follow the format [text](url).
 */
export class UrlAction extends TextEditorAction {
    static readonly ID = 'url.action';
    static readonly DEFAULT_INSERT_TEXT = '[](https://)';

    // We remove the global flag from the URL regex to avoid stateful behavior.
    private statelessUrlRegex = new RegExp(`^${urlRegex.source}$`, urlRegex.flags.replace('g', ''));
    private disposableSelectionChangeListener?: Disposable;
    private disposablePasteListener?: Disposable;

    private selectedText: { previous?: string; current?: string } = {};

    constructor() {
        super(UrlAction.ID, 'artemisApp.multipleChoiceQuestion.editor.link', faLink, undefined);
    }

    register(editor: TextEditor, translateService: TranslateService): void {
        super.register(editor, translateService);
        this.disposableSelectionChangeListener = editor.addSelectionChangeListener((selectedText) => {
            // We track the current and previous selections to check the before and after text when pasting a URL.
            this.selectedText = { previous: this.selectedText.current, current: selectedText };
        });

        this.disposablePasteListener = editor.addPasteListener((editor, range) => {
            const replacedText = this.selectedText.previous ?? '';
            const newText = editor.getTextAtRange(range);
            if (replacedText.length && this.statelessUrlRegex.test(newText)) {
                // Undo the paste. We want to apply special formatting to the selected text.
                editor.undo();
                // The selection is now what it was before the paste, so we can replace it with the formatted text.
                this.replaceTextAtCurrentSelection(editor, `[${replacedText}](${newText})`);
            }
        });
    }

    dispose(): void {
        super.dispose();
        this.disposablePasteListener?.dispose();
        this.disposableSelectionChangeListener?.dispose();
        this.selectedText = {};
    }

    /**
     * Executes the action in the current editor with the given arguments (url and text).
     * @param args The text and url of the URL to insert. If one or both are not provided, the default text will be inserted.
     */
    executeInCurrentEditor(args?: UrlArguments): void {
        super.executeInCurrentEditor(args);
    }

    /**
     * Inserts, at the current selection, the markdown URL with the given text and url if they were provided, or the default text otherwise.
     * @param editor The editor in which to insert the URL.
     * @param args The text and url of the URL to insert. If one or both are not provided, the default text will be inserted.
     */
    run(editor: TextEditor, args?: UrlArguments): void {
        if (!args?.text || !args?.url) {
            this.replaceTextAtCurrentSelection(editor, UrlAction.DEFAULT_INSERT_TEXT);
        } else {
            this.replaceTextAtCurrentSelection(editor, `[${args.text}](${args.url})`);
        }
        editor.focus();
    }
}

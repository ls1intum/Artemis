import { faImage } from '@fortawesome/free-solid-svg-icons';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { sanitizeStringForMarkdownEditor } from 'app/shared/util/markdown.util';
import { Disposable } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { TranslateService } from '@ngx-translate/core';

interface AttachmentArguments {
    text: string;
    url: string;
}

/**
 * Action to insert an attachment into the editor. They follow the format ![text](url).
 */
export class AttachmentAction extends TextEditorAction {
    static readonly ID = 'attachment.action';
    static readonly DEFAULT_INSERT_TEXT = '![](https://)';

    private disposablePasteListener?: Disposable;
    private uploadCallback?: (files: File[]) => void;

    constructor() {
        super(AttachmentAction.ID, 'artemisApp.multipleChoiceQuestion.editor.imageUpload', faImage, undefined);
    }

    /**
     * Sets the callback to be called when files are pasted into the editor. The callback will be reset to undefined when the action is disposed.
     * @param callback The callback to call when files are pasted into the editor.
     */
    setUploadCallback(callback?: (files: File[]) => void) {
        this.uploadCallback = callback;
    }

    register(editor: TextEditor, translateService: TranslateService) {
        super.register(editor, translateService);
        this.disposablePasteListener = editor.addPasteListener(async (insertedText: string) => {
            // We do not read from the clipboard if the user pasted text. This prevents an unnecessary prompt on Firefox.
            if (!this.uploadCallback || insertedText) {
                return;
            }
            const clipboardItems = await navigator.clipboard.read();
            const files: File[] = [];
            for (const clipboardItem of clipboardItems) {
                for (const type of clipboardItem.types) {
                    if (type.startsWith('image/')) {
                        // Map image type to extension.
                        const extension = type.replace('image/', '');
                        const blob = await clipboardItem.getType(type);
                        files.push(new File([blob], `image.${extension}`, { type }));
                        break;
                    }
                }
            }
            this.uploadCallback(files);
        });
    }

    dispose() {
        super.dispose();
        this.disposablePasteListener?.dispose();
        this.uploadCallback = undefined;
    }

    /**
     * Executes the action in the current editor with the given arguments (url and text).
     * @param args The text and url of the attachment to insert. If one or both are not provided, checks for selected text to wrap.
     */
    executeInCurrentEditor(args?: AttachmentArguments): void {
        super.executeInCurrentEditor(args);
    }

    /**
     * Inserts, at the current selection, the attachment markdown with the given text and url if they were provided.
     * If no arguments are provided and there is selected text, wraps the selected text with ![selectedText](https://).
     * Otherwise, inserts the default text.
     * @param editor The editor in which to insert the attachment.
     * @param args The text and url of the attachment to insert. If one or both are not provided, checks for selected text to wrap.
     */
    run(editor: TextEditor, args?: AttachmentArguments): void {
        if (!args?.text || !args?.url) {
            this.wrapSelectionOrInsertDefault(editor, (selectedText) => `![${sanitizeStringForMarkdownEditor(selectedText)}](https://)`, AttachmentAction.DEFAULT_INSERT_TEXT);
        } else {
            this.replaceTextAtCurrentSelection(editor, `![${sanitizeStringForMarkdownEditor(args.text)}](${args.url})`);
        }
        editor.focus();
    }
}

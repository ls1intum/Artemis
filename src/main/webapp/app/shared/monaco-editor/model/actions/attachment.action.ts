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
    private openFileDialogCallback?: () => void;

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

    /**
     * Sets the callback to be called when the attachment action is triggered without arguments and no text is selected.
     * This allows opening a file dialog for manual file selection. The callback will be reset to undefined when the action is disposed.
     * @param callback The callback to call when a file dialog should be opened.
     */
    setOpenFileDialogCallback(callback?: () => void) {
        this.openFileDialogCallback = callback;
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
        this.openFileDialogCallback = undefined;
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
     * If no arguments are provided:
     * - If there is selected text, wraps it with ![selectedText](https://)
     * - If there is no selected text and a file dialog callback is configured, opens the file dialog
     * - Otherwise, inserts the default text
     * @param editor The editor in which to insert the attachment.
     * @param args The text and url of the attachment to insert. If one or both are not provided, checks for selected text or opens file dialog.
     */
    run(editor: TextEditor, args?: AttachmentArguments): void {
        const selectedText = this.getSelectedText(editor)?.trim();
        if (!args?.text || !args?.url) {
            if (selectedText) {
                this.wrapSelectionOrInsertDefault(editor, (text) => `![${sanitizeStringForMarkdownEditor(text)}](https://)`, AttachmentAction.DEFAULT_INSERT_TEXT);
            } else if (this.openFileDialogCallback) {
                this.openFileDialogCallback();
            } else {
                this.wrapSelectionOrInsertDefault(editor, () => AttachmentAction.DEFAULT_INSERT_TEXT, AttachmentAction.DEFAULT_INSERT_TEXT);
            }
        } else {
            this.replaceTextAtCurrentSelection(editor, `![${sanitizeStringForMarkdownEditor(args.text)}](${args.url})`);
        }
        editor.focus();
    }
}

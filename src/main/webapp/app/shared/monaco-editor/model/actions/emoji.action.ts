import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { faSmile } from '@fortawesome/free-solid-svg-icons';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { ViewContainerRef } from '@angular/core';
import { EmojiPickerComponent } from 'app/shared/metis/emoji/emoji-picker.component';
import { Overlay, OverlayPositionBuilder, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { TextEditorPosition } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-position.model';
import { TextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';

/**
 * Action to open the emoji picker and insert the selected emoji into the editor.
 */
export class EmojiAction extends TextEditorAction {
    static readonly ID = 'emoji.action';
    private overlayRef: OverlayRef | null = null;
    private position?: { x: number; y: number };

    constructor(
        private viewContainerRef: ViewContainerRef,
        private overlay: Overlay,
        private positionBuilder: OverlayPositionBuilder,
    ) {
        super(EmojiAction.ID, 'artemisApp.multipleChoiceQuestion.editor.emoji', faSmile, undefined);
    }

    /**
     * Sets the position where the emoji picker should appear.
     * @param param The {x, y} coordinates.
     */
    setPoint(param: { x: number; y: number }): void {
        this.position = { x: param.x, y: param.y };
    }

    /**
     * Triggers the opening of the emoji picker and attaches it to the view container.
     * @param editor The editor in which to insert the emoji.
     */
    run(editor: TextEditor): void {
        if (this.overlayRef) {
            this.destroyEmojiPicker();
            return;
        }

        if (this.position) {
            this.createEmojiPicker(editor, this.position);
        }
    }

    /**
     * Creates and attaches the emoji picker component dynamically and handles emoji selection.
     * @param editor The editor instance where the emoji will be inserted.
     * @param position The {x, y} coordinates where the picker should appear.
     */
    private createEmojiPicker(editor: TextEditor, position: { x: number; y: number }): void {
        const positionStrategy = this.positionBuilder
            .global()
            .left(`${position.x - 15}px`)
            .top(`${position.y - 15}px`);

        this.overlayRef = this.overlay.create({
            positionStrategy,
            hasBackdrop: true,
            backdropClass: 'cdk-overlay-transparent-backdrop',
            scrollStrategy: this.overlay.scrollStrategies.reposition(),
            width: '0',
        });

        const emojiPickerPortal = new ComponentPortal(EmojiPickerComponent, this.viewContainerRef);
        const componentRef = this.overlayRef.attach(emojiPickerPortal);
        const pickerElement = componentRef.location.nativeElement;
        pickerElement.style.transform = 'translate(-100%, -100%)';

        componentRef.instance.emojiSelect.subscribe((selection: { emoji: any; event: PointerEvent }) => {
            this.insertEmojiAtCursor(editor, selection.emoji.native);
            this.destroyEmojiPicker();
        });

        this.overlayRef.backdropClick().subscribe(() => {
            this.destroyEmojiPicker();
        });
    }

    /**
     * Inserts the selected emoji into the editor at the current cursor position.
     * @param editor The editor instance.
     * @param emoji The emoji to insert.
     */
    insertEmojiAtCursor(editor: TextEditor, emoji: string): void {
        const position = editor.getPosition();
        if (!position) return;

        this.insertTextAtPosition(editor, position, emoji);

        const newPosition = new TextEditorPosition(position.getLineNumber(), position.getColumn() + 2);
        editor.setPosition(newPosition);
        editor.focus();
    }

    /**
     * Inserts the given emoji text at the current cursor position.
     * @param editor The editor instance.
     * @param position The current cursor position.
     * @param emoji The emoji text to insert.
     */
    insertTextAtPosition(editor: TextEditor, position: TextEditorPosition, emoji: string): void {
        this.replaceTextAtRange(editor, new TextEditorRange(position, position), emoji);
    }

    /**
     * Destroys the emoji picker component after an emoji is selected or toggled.
     */
    private destroyEmojiPicker(): void {
        if (this.overlayRef) {
            this.overlayRef.dispose();
            this.overlayRef = null;
        }
    }
}

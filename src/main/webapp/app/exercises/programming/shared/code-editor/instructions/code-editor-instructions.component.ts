import { AfterViewInit, Component, EventEmitter, OnDestroy, Output } from '@angular/core';
import { Interactable } from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { Subscription } from 'rxjs';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { WindowRef } from 'app/core/websocket/window.service';
import { CodeEditorGridService } from 'app/exercises/programming/shared/code-editor/service/code-editor-grid.service';
import { ResizeType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-code-editor-instructions',
    styleUrls: ['./code-editor-instructions.scss'],
    templateUrl: './code-editor-instructions.component.html',
})
export class CodeEditorInstructionsComponent implements AfterViewInit, OnDestroy {
    @Output()
    onToggleCollapse = new EventEmitter<{ event: any; horizontal: boolean; interactable: Interactable; resizableMinWidth?: number; resizableMinHeight?: number }>();

    /** Resizable constants **/
    initialInstructionsWidth: number;
    minInstructionsWidth: number;
    interactResizable: Interactable;

    resizeSubscription: Subscription;

    constructor(private $window: WindowRef, public artemisMarkdown: ArtemisMarkdownService, private codeEditorGridService: CodeEditorGridService) {}

    /**
     * After the view was initialized, we create an interact.js resizable object,
     * designate the edges which can be used to resize the target element and set min and max values.
     * The 'resizemove' callback function processes the event values and sets new width and height values for the element.
     */
    ngAfterViewInit(): void {
        this.initialInstructionsWidth = this.$window.nativeWindow.screen.width - 300 / 2;
        this.minInstructionsWidth = this.$window.nativeWindow.screen.width / 4 - 50;
        this.interactResizable = interact('.resizable-instructions');

        this.resizeSubscription = this.codeEditorGridService.subscribeForResizeEvents([ResizeType.SIDEBAR_RIGHT, ResizeType.MAIN_BOTTOM]).subscribe(() => {
            //if (this.editableInstructions && this.editableInstructions.markdownEditor && this.editableInstructions.markdownEditor.aceEditorContainer) {
            //    this.editableInstructions.markdownEditor.aceEditorContainer.getEditor().resize();
            //}
        });
    }

    /**
     * If there is a subscription do unsubscribe.
     */
    ngOnDestroy(): void {
        if (this.resizeSubscription) {
            this.resizeSubscription.unsubscribe();
        }
    }

    /**
     * Calls the parent (editorComponent) toggleCollapse method
     * @param $event - any event
     */
    toggleEditorCollapse($event: any) {
        this.onToggleCollapse.emit({ event: $event, horizontal: true, interactable: this.interactResizable, resizableMinWidth: this.minInstructionsWidth });
    }
}

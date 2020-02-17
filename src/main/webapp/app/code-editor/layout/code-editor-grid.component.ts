import * as $ from 'jquery';
import { AfterViewInit, Component, ContentChild, ElementRef, Input, ViewEncapsulation } from '@angular/core';
import { AlertService } from 'app/core/alert/alert.service';

import { WindowRef } from 'app/core/websocket/window.service';
import Interactable from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { CodeEditorGridService } from 'app/code-editor/service/code-editor-grid.service';
import { ResizeType } from 'app/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-code-editor-grid',
    templateUrl: './code-editor-grid.component.html',
    styleUrls: ['./code-editor-grid.scss'],
    providers: [WindowRef, CodeEditorGridService],
    encapsulation: ViewEncapsulation.None,
})
export class CodeEditorGridComponent implements AfterViewInit {
    @ContentChild('editorSidebarRight', { static: false }) editorSidebarRight: ElementRef;
    @ContentChild('editorSidebarLeft', { static: false }) editorSidebarLeft: ElementRef;
    @ContentChild('editorBottomArea', { static: false }) editorBottomArea: ElementRef;

    @Input()
    exerciseTitle: string;

    interactResizableMain: Interactable;
    resizableMinHeightMain = 480;
    resizableMaxHeightMain = 1200;

    interactResizableLeft: Interactable;
    resizableMinWidthLeft: number;
    resizableMaxWidthLeft = 1200;

    interactResizableRight: Interactable;
    resizableMinWidthRight: number;
    resizableMaxWidthRight = 1200;

    interactResizableBottom: Interactable;
    resizableMinHeightBottom = 300;
    resizableMaxHeightBottom = 600;

    constructor(private $window: WindowRef, private codeEditorGridService: CodeEditorGridService) {}

    /**
     * @function ngAfterViewInit
     * @desc After the view was initialized, we create an interact.js resizable object,
     *       designate the edges which can be used to resize the target element and set min and max values.
     *       The 'resizemove' callback function processes the event values and sets new width and height values for the element.
     */
    ngAfterViewInit(): void {
        this.resizableMinHeightMain = this.$window.nativeWindow.screen.height / 3;
        this.interactResizableMain = interact('.editor-main')
            .resizable({
                // Enable resize from bottom edge; triggered by class rg-bottom
                edges: { left: false, right: false, bottom: '.rg-main-bottom', top: false },
                // Set min and max height
                modifiers: [
                    interact.modifiers!.restrictSize({
                        min: { width: 0, height: this.resizableMinHeightMain },
                        max: { width: 2000, height: this.resizableMaxHeightMain },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
                this.codeEditorGridService.submitResizeEvent(ResizeType.MAIN_BOTTOM);
            })
            .on('resizemove', function(event: any) {
                const target = event.target;
                // Update element height
                target.style.height = event.rect.height + 'px';
            });

        this.resizableMinWidthLeft = this.$window.nativeWindow.screen.width / 7;
        this.resizableMaxWidthLeft = this.$window.nativeWindow.screen.width / 2;
        this.interactResizableLeft = interact('.editor-sidebar-left')
            .resizable({
                // Enable resize from bottom edge; triggered by class rg-bottom
                edges: { left: false, right: '.rg-sidebar-left', bottom: false, top: false },
                // Set min and max height
                modifiers: [
                    interact.modifiers!.restrictSize({
                        min: { width: this.resizableMinWidthLeft, height: 0 },
                        max: { width: this.resizableMaxWidthLeft, height: 2000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
                this.codeEditorGridService.submitResizeEvent(ResizeType.SIDEBAR_LEFT);
            })
            .on('resizemove', function(event: any) {
                const target = event.target;
                // Update element height
                target.style.width = event.rect.width + 'px';
            });

        this.resizableMinWidthRight = this.$window.nativeWindow.screen.width / 6;
        this.resizableMaxWidthRight = this.$window.nativeWindow.screen.width / 2;
        this.interactResizableRight = interact('.editor-sidebar-right')
            .resizable({
                // Enable resize from bottom edge; triggered by class rg-bottom
                edges: { left: '.rg-sidebar-right', right: false, bottom: false, top: false },
                // Set min and max height
                modifiers: [
                    interact.modifiers!.restrictSize({
                        min: { width: this.resizableMinWidthRight, height: 0 },
                        max: { width: this.resizableMaxWidthRight, height: 2000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
                this.codeEditorGridService.submitResizeEvent(ResizeType.SIDEBAR_RIGHT);
            })
            .on('resizemove', function(event: any) {
                const target = event.target;
                // Update element height
                target.style.width = event.rect.width + 'px';
            });

        this.resizableMinHeightBottom = this.$window.nativeWindow.screen.height / 6;
        this.interactResizableBottom = interact('.editor-bottom')
            .resizable({
                // Enable resize from bottom edge; triggered by class rg-bottom
                edges: { left: false, right: false, bottom: '.rg-bottom-bottom', top: false },
                // Set min and max height
                modifiers: [
                    interact.modifiers!.restrictSize({
                        min: { width: 0, height: this.resizableMinHeightBottom },
                        max: { width: this.$window.nativeWindow.screen.width, height: this.resizableMaxHeightBottom },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
                this.codeEditorGridService.submitResizeEvent(ResizeType.BOTTOM);
            })
            .on('resizemove', function(event: any) {
                const target = event.target;
                // Update element height
                target.style.height = event.rect.height + 'px';
            });
    }

    /**
     * @function toggleCollapse
     * @desc Collapse parts of the editor (file browser, build output...)
     * @param $event {object} Click event object; contains target information
     * @param horizontal {boolean} Used to decide which height to use for the collapsed element
     * @param interactResizable {Interactable} The interactjs element, used to en-/disable resizing
     * @param minWidth {number} Width to set the element to after toggling the collapse
     * @param minHeight {number} Height to set the element to after toggling the collapse
     */
    toggleCollapse($event: any, horizontal: boolean, interactResizable: Interactable, minWidth?: number, minHeight?: number) {
        const target = $event.toElement || $event.relatedTarget || $event.target;
        target.blur();
        const $card = $(target).closest('.collapsable');
        const collapsed = `collapsed--${horizontal ? 'horizontal' : 'vertical'}`;

        if ($card.hasClass(collapsed)) {
            $card.removeClass(collapsed);
            interactResizable.resizable({ enabled: true });
        } else {
            $card.addClass(collapsed);
            interactResizable.resizable({ enabled: false });
        }
    }
}

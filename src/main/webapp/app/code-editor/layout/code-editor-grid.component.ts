import * as $ from 'jquery';
import { AfterViewInit, Component, ContentChild, ElementRef, Input } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';

import { CourseService } from 'app/entities/course';
import { WindowRef } from 'app/core/websocket/window.service';
import Interactable from '@interactjs/core/Interactable';
import interact from 'interactjs';

@Component({
    selector: 'jhi-code-editor-grid',
    templateUrl: './code-editor-grid.component.html',
    providers: [JhiAlertService, WindowRef, CourseService],
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

    constructor(private $window: WindowRef) {}

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
                restrictSize: {
                    min: { height: this.resizableMinHeightMain },
                    max: { height: this.resizableMaxHeightMain },
                },
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function(event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function(event: any) {
                const target = event.target;
                // Update element height
                target.style.height = event.rect.height + 'px';
            });

        this.resizableMinWidthLeft = this.$window.nativeWindow.screen.width / 6;
        this.resizableMaxWidthLeft = this.$window.nativeWindow.screen.width / 4;
        this.interactResizableLeft = interact('.editor-sidebar-left')
            .resizable({
                // Enable resize from bottom edge; triggered by class rg-bottom
                edges: { left: false, right: '.rg-sidebar-left', bottom: false, top: false },
                // Set min and max height
                restrictSize: {
                    min: { width: this.resizableMinWidthLeft },
                    max: { width: this.resizableMaxWidthLeft },
                },
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function(event: any) {
                event.target.classList.remove('card-resizable');
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
                restrictSize: {
                    min: { width: this.resizableMinWidthRight },
                    max: { width: this.resizableMaxWidthRight },
                },
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function(event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function(event: any) {
                const target = event.target;
                // Update element height
                target.style.width = event.rect.width + 'px';
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

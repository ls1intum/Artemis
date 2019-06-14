import * as $ from 'jquery';
import { Component, ContentChild, ElementRef, Input } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';

import { CourseService } from 'app/entities/course';
import { WindowRef } from 'app/core/websocket/window.service';
import Interactable from '@interactjs/core/Interactable';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

@Component({
    selector: 'jhi-code-editor-grid',
    templateUrl: './code-editor-grid.component.html',
    providers: [JhiAlertService, WindowRef, CourseService],
})
export class CodeEditorGridComponent {
    @ContentChild('editorSidebarRight', { static: false }) editorSidebarRight: ElementRef;
    @ContentChild('editorSidebarLeft', { static: false }) editorSidebarLeft: ElementRef;
    @ContentChild('editorBottomArea', { static: false }) editorBottomArea: ElementRef;

    @Input()
    exerciseTitle: string;

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
        const $card = $(target).closest('.card');

        if ($card.hasClass('collapsed')) {
            $card.removeClass('collapsed');
            interactResizable.resizable({ enabled: true });

            // Reset min width if argument was provided
            if (minWidth) {
                $card.width(minWidth + 'px');
            }
            // Reset min height if argument was provided
            if (minHeight) {
                $card.height(minHeight + 'px');
            }
        } else {
            $card.addClass('collapsed');
            horizontal ? $card.width('55px') : $card.height('35px');
            interactResizable.resizable({ enabled: false });
        }
    }
}

import * as $ from 'jquery';
import { ContentChild, Component, ElementRef, Input, OnInit, OnChanges, ViewChild, SimpleChanges } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { Subscription } from 'rxjs/Subscription';
import { difference as _difference } from 'lodash';
import { compose, filter, fromPairs, map, toPairs } from 'lodash/fp';
import { catchError, map as rxMap, switchMap, tap } from 'rxjs/operators';

import { CourseService } from '../entities/course';
import { WindowRef } from '../core/websocket/window.service';
import Interactable from '@interactjs/core/Interactable';
import { CodeEditorAceComponent } from 'app/code-editor/ace/code-editor-ace.component';
import { EditorState, CommitState } from 'app/entities/ace-editor';
import { Observable, Subject } from 'rxjs';
import { FileChange, RenameFileChange, CreateFileChange, DeleteFileChange, FileType } from 'app/entities/ace-editor/file-change.model';
import { IRepositoryService, IRepositoryFileService, DomainService } from './code-editor-repository.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { AnnotationArray, Session } from '../entities/ace-editor';
import { BuildLogEntryArray } from 'app/entities/build-log';
import { CodeEditorSessionService } from './code-editor-session.service';

@Component({
    selector: 'jhi-code-editor',
    templateUrl: './code-editor.component.html',
    providers: [JhiAlertService, WindowRef, CourseService],
})
export class CodeEditorComponent {
    @ViewChild(CodeEditorAceComponent) editor: CodeEditorAceComponent;
    @ContentChild('editor-sidebar-right') editorSidebarRight: ElementRef;
    @ContentChild('editor-bottom-area') editorBottomArea: ElementRef;

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
            horizontal ? $card.height('35px') : $card.width('55px');
            interactResizable.resizable({ enabled: false });
        }
    }
}

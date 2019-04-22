import { AfterViewInit, Component, Input } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import * as interact from 'interactjs';

import { ArtemisMarkdown } from 'app/components/util/markdown.service';

import { CodeEditorComponent } from '../code-editor.component';
import { CodeEditorService } from '../code-editor.service';
import { Participation } from '../../entities/participation';
import { RepositoryService } from '../../entities/repository/repository.service';
import { ResultService } from '../../entities/result';
import { WindowRef } from '../../core/websocket/window.service';

@Component({
    selector: 'jhi-code-editor-instructions',
    templateUrl: './code-editor-instructions.component.html',
    providers: [JhiAlertService, WindowRef, RepositoryService, ResultService, CodeEditorService],
})
export class CodeEditorInstructionsComponent implements AfterViewInit {
    haveDetailsBeenLoaded = false;

    /** Resizable constants **/
    initialInstructionsWidth: number;
    minInstructionsWidth: number;
    interactResizable: interact.Interactable;
    noInstructionsAvailable = false;

    @Input()
    participation: Participation;

    constructor(private parent: CodeEditorComponent, private $window: WindowRef, public artemisMarkdown: ArtemisMarkdown) {}

    /**
     * @function ngAfterViewInit
     * @desc After the view was initialized, we create an interact.js resizable object,
     *       designate the edges which can be used to resize the target element and set min and max values.
     *       The 'resizemove' callback function processes the event values and sets new width and height values for the element.
     */
    ngAfterViewInit(): void {
        this.initialInstructionsWidth = this.$window.nativeWindow.screen.width - 300 / 2;
        this.minInstructionsWidth = this.$window.nativeWindow.screen.width / 4 - 50;
        this.interactResizable = interact('.resizable-instructions')
            .resizable({
                // Enable resize from left edge; triggered by class rg-left
                edges: { left: '.rg-left', right: false, bottom: false, top: false },
                // Set maximum width
                restrictSize: {
                    min: { width: this.minInstructionsWidth },
                    max: { width: this.initialInstructionsWidth },
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
                // Update element width
                target.style.width = event.rect.width + 'px';
            });
    }

    /**
     * @function toggleEditorCollapse
     * @desc Calls the parent (editorComponent) toggleCollapse method
     * @param $event
     * @param {boolean} horizontal
     */
    toggleEditorCollapse($event: any, horizontal: boolean) {
        this.parent.toggleCollapse($event, horizontal, this.interactResizable, this.minInstructionsWidth);
    }

    onNoInstructionsAvailable() {
        this.noInstructionsAvailable = true;
    }
}

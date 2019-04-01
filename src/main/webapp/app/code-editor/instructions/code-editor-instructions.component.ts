import { AfterViewInit, Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import * as interact from 'interactjs';

import { ArtemisMarkdown } from 'app/components/util/markdown.service';

import { CodeEditorComponent } from '../code-editor.component';
import { CodeEditorService } from '../code-editor.service';
import { Feedback } from '../../entities/feedback';
import { Participation } from '../../entities/participation';
import { RepositoryFileService, RepositoryService } from '../../entities/repository/repository.service';
import { Result, ResultService } from '../../entities/result';
import { WindowRef } from '../../core/websocket/window.service';

@Component({
    selector: 'jhi-code-editor-instructions',
    templateUrl: './code-editor-instructions.component.html',
    providers: [JhiAlertService, WindowRef, RepositoryService, ResultService, CodeEditorService],
})
export class CodeEditorInstructionsComponent implements AfterViewInit, OnChanges {
    haveDetailsBeenLoaded = false;

    /** Resizable constants **/
    initialInstructionsWidth: number;
    minInstructionsWidth: number;
    interactResizable: interact.Interactable;
    noInstructionsAvailable = false;

    @Input()
    participation: Participation;
    @Input()
    latestResult: Result;

    constructor(private parent: CodeEditorComponent, private $window: WindowRef, private repositoryFileService: RepositoryFileService, public artemisMarkdown: ArtemisMarkdown) {}

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
            .on('resizemove', function(event: any) {
                const target = event.target;
                // Update element width
                target.style.width = event.rect.width + 'px';
            });
    }

    /**
     * @function ngOnChanges
     * @desc New participation received => initialize new Remarkable object and load new README.md file
     *       New latestResult received => load details for result
     * @param {SimpleChanges} changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        // If there is no problemStatement in the exercise, fall back to loading the Readme (old solution)
        if (changes.participation && this.participation) {
            // Initialize array for listener remove functions
            this.loadInstructions();
        }
    }

    /**
     * @function loadInstructions
     * @desc Loads the instructions for the programming exercise.
     * We added the problemStatement later, historically the instructions where a file in the student's repository
     * This is why we now prefer the problemStatement and if it doesn't exist try to load the readme.
     */
    loadInstructions() {
        if (!this.participation.exercise.problemStatement) {
            this.repositoryFileService.get(this.participation.id, 'README.md').subscribe(
                fileObj => {
                    this.participation.exercise.problemStatement = fileObj.fileContent;
                },
                err => {
                    // TODO: handle the case that there is no README.md file
                    this.noInstructionsAvailable = true;
                    console.log('Error while getting README.md file!', err);
                },
            );
        }
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
}

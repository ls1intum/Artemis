import { AfterViewInit, Component, EventEmitter, Input, Output, OnChanges, SimpleChanges } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import Interactable from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { catchError, tap } from 'rxjs/operators';
import { of } from 'rxjs';

import { ArtemisMarkdown } from 'app/components/util/markdown.service';

import { CodeEditorService } from '../service/code-editor.service';
import { Participation } from '../../entities/participation';
import { ResultService } from '../../entities/result';
import { WindowRef } from '../../core/websocket/window.service';
import { ProgrammingExercise, ProgrammingExerciseService } from 'app/entities/programming-exercise';
import { MarkdownEditorHeight } from 'app/markdown-editor';

@Component({
    selector: 'jhi-code-editor-instructions',
    templateUrl: './code-editor-instructions.component.html',
    providers: [JhiAlertService, WindowRef, ResultService, CodeEditorService],
})
export class CodeEditorInstructionsComponent implements AfterViewInit {
    MarkdownEditorHeight = MarkdownEditorHeight;

    @Input()
    participation: Participation;
    @Input()
    exercise: ProgrammingExercise;
    @Input()
    editableInstructions = false;
    @Output()
    onError = new EventEmitter<string>();
    @Output()
    onToggleCollapse = new EventEmitter<{ event: any; horizontal: boolean; interactable: Interactable; resizableMinWidth: number }>();

    /** Resizable constants **/
    initialInstructionsWidth: number;
    minInstructionsWidth: number;
    interactResizable: Interactable;
    noInstructionsAvailable = false;

    // Only relevant if instructions are editable
    savingInstructions = false;
    unsavedChanges = false;

    constructor(private $window: WindowRef, public artemisMarkdown: ArtemisMarkdown, private programmingExerciseService: ProgrammingExerciseService) {}

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

    onProblemStatementEditorUpdate(newProblemStatement: string) {
        if (newProblemStatement !== this.exercise.problemStatement) {
            this.unsavedChanges = true;
            this.exercise = { ...this.exercise, problemStatement: newProblemStatement };
        }
    }

    saveInstructions($event: any) {
        $event.stopPropagation();
        this.savingInstructions = true;
        return this.programmingExerciseService
            .updateProblemStatement(this.exercise.id, this.exercise.problemStatement)
            .pipe(
                tap(() => {
                    this.unsavedChanges = false;
                }),
                catchError(() => {
                    this.onError.emit('problemStatementCouldNotBeUpdated');
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.savingInstructions = false;
            });
    }

    /**
     * @function toggleEditorCollapse
     * @desc Calls the parent (editorComponent) toggleCollapse method
     * @param $event
     * @param {boolean} horizontal
     */
    toggleEditorCollapse($event: any, horizontal: boolean) {
        this.onToggleCollapse.emit({ event: $event, horizontal, interactable: this.interactResizable, resizableMinWidth: this.minInstructionsWidth });
    }

    onNoInstructionsAvailable() {
        this.noInstructionsAvailable = true;
    }
}

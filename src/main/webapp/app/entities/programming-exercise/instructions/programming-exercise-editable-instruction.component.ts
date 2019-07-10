import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, ViewChild } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import Interactable from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { of } from 'rxjs';
import { map as rxMap, filter as rxFilter } from 'rxjs/operators';
import { catchError, tap } from 'rxjs/operators';
import { Participation } from 'app/entities/participation';
import { compose, filter, map, sortBy } from 'lodash/fp';
import { ProgrammingExercise } from '../programming-exercise.model';
import { DomainCommand } from 'app/markdown-editor/domainCommands';
import { TaskCommand } from 'app/markdown-editor/domainCommands/programming-exercise/task.command';
import { TestCaseCommand } from 'app/markdown-editor/domainCommands/programming-exercise/testCase.command';
import { ApollonCommand } from 'app/markdown-editor/domainCommands/apollon.command';
import { MarkdownEditorComponent } from 'app/markdown-editor';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/services';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';
import { Result, ResultService } from 'app/entities/result';

@Component({
    selector: 'jhi-programming-exercise-editable-instructions',
    templateUrl: './programming-exercise-editable-instruction.component.html',
    styleUrls: ['./programming-exercise-editable-instruction.scss'],
})
export class ProgrammingExerciseEditableInstructionComponent implements AfterViewInit {
    participationValue: Participation;
    exerciseValue: ProgrammingExercise;

    exerciseTestCases: string[] = [];

    taskCommand = new TaskCommand();
    taskRegex = this.taskCommand.getTagRegex('g');
    testCaseCommand = new TestCaseCommand();
    apollonCommand = new ApollonCommand(this.modalService);

    domainCommands: DomainCommand[] = [this.taskCommand, this.testCaseCommand, this.apollonCommand];

    savingInstructions = false;
    unsavedChanges = false;

    interactResizable: Interactable;

    @ViewChild(MarkdownEditorComponent, { static: false }) markdownEditor: MarkdownEditorComponent;

    @Input() showStatus = true;
    @Input() enableSave = true;
    @Input() enableResize = true;
    @Input() showSaveButton = false;
    @Input() templateParticipation: Participation;
    @Input()
    get exercise() {
        return this.exerciseValue;
    }
    @Input()
    get participation() {
        return this.participationValue;
    }
    @Output() participationChange = new EventEmitter<Participation>();
    @Output() exerciseChange = new EventEmitter<ProgrammingExercise>();

    set participation(participation: Participation) {
        this.participationValue = participation;
        this.participationChange.emit(this.participationValue);
    }

    set exercise(exercise: ProgrammingExercise) {
        if (this.exerciseValue && exercise.problemStatement !== this.exerciseValue.problemStatement) {
            this.unsavedChanges = true;
        }
        this.exerciseValue = exercise;
        this.exerciseChange.emit(this.exerciseValue);
    }

    constructor(
        private modalService: NgbModal,
        private programmingExerciseService: ProgrammingExerciseService,
        private jhiAlertService: JhiAlertService,
        private resultService: ResultService,
    ) {}

    ngAfterViewInit() {
        this.interactResizable = interact('.editable-instruction-container')
            .resizable({
                // Enable resize from top edge; triggered by class rg-top
                edges: { left: false, right: false, bottom: '.rg-bottom', top: false },
                // Set min and max height
                restrictSize: {
                    min: { height: 200 },
                    max: { height: 1200 },
                },
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
                this.markdownEditor.aceEditorContainer.getEditor().resize();
            })
            .on('resizemove', function(event: any) {
                // The first child is the markdown editor.
                const target = event.target.children && event.target.children[0];
                if (target) {
                    // Update element height
                    target.style.height = event.rect.height + 'px';
                }
            });
    }

    /* Save the problem statement on the server.
     * @param $event
     */
    saveInstructions($event: any) {
        $event.stopPropagation();
        this.savingInstructions = true;
        return this.programmingExerciseService
            .updateProblemStatement(this.exercise.id, this.exercise.problemStatement!)
            .pipe(
                tap(() => {
                    this.unsavedChanges = false;
                }),
                catchError(() => {
                    // TODO: move to programming exercise translations
                    this.jhiAlertService.error(`artemisApp.editor.errors.problemStatementCouldNotBeUpdated`);
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.savingInstructions = false;
            });
    }

    updateProblemStatement(problemStatement: string) {
        if (this.exercise.problemStatement !== problemStatement) {
            this.exercise = { ...this.exercise, problemStatement };
            this.unsavedChanges = true;
        }
    }

    updateTestCases = (testCases: ProgrammingExerciseTestCase[]) => {
        if (testCases) {
            setTimeout(() => {
                this.exerciseTestCases = compose(
                    map(({ testName }) => testName),
                    filter(({ active }) => active),
                    sortBy('testName'),
                )(testCases);
                this.testCaseCommand.setValues(this.exerciseTestCases);
            }, 0);
        } else if (this.exercise.templateParticipation) {
            // Fallback for exercises that don't have test cases yet.
            this.resultService
                .getLatestResultWithFeedbacks(this.exercise.templateParticipation.id)
                .pipe(
                    rxMap((res: HttpResponse<Result>) => res.body),
                    rxFilter((result: Result) => !!result.feedbacks),
                    rxMap(({ feedbacks }: Result) =>
                        compose(
                            map(({ text }) => text),
                            sortBy('text'),
                        )(feedbacks),
                    ),
                )
                .subscribe((_testCases: string[]) => {
                    this.exerciseTestCases = _testCases;
                    this.testCaseCommand.setValues(this.exerciseTestCases);
                });
        }
    };
}

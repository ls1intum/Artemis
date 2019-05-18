import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { UMLModel } from '@ls1intum/apollon';
import { Conflict } from 'app/modeling-assessment-editor/conflict.model';
import * as $ from 'jquery';
import { JhiAlertService } from 'ng-jhipster';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { Feedback } from 'app/entities/feedback';
import { ConflictResolutionState } from 'app/modeling-assessment-editor/conflict-resolution-state.enum';

@Component({
    selector: 'jhi-modeling-assessment-conflict',
    templateUrl: './modeling-assessment-conflict.component.html',
    styleUrls: ['./modeling-assessment-conflict.component.scss', '../modeling-assessment-editor/modeling-assessment-editor.component.scss'],
})
export class ModelingAssessmentConflictComponent implements OnInit, AfterViewInit, OnChanges {
    // mergedFeedbacks: Feedback[];
    currentFeedbacksCopy: Feedback[];
    highlightColor: string;
    // user: User;

    // currentConflict: Conflict;
    // conflictingResult: ConflictingResult;

    @Input() modelingExercise: ModelingExercise;
    @Input() leftModel: UMLModel;
    @Input() leftFeedbacks: Feedback[];
    @Input() leftHighlightedElementIds: Set<string> = new Set<string>();
    @Input() leftCenteredElementId: string;
    @Input() rightFeedback: Feedback[];
    @Input() rightModel: UMLModel;
    @Input() rightHighlightedElementIds: Set<string> = new Set<string>();
    @Input() rightCenteredElementId: string;
    @Input() rightAssessmentReadOnly = false;
    @Input() conflictState: ConflictResolutionState = ConflictResolutionState.UNHANDLED;
    @Output() escalate = new EventEmitter<{ escalatedConflicts: Conflict[]; newFeedbacks: Feedback[] }>();
    @Output() leftButtonPressed = new EventEmitter();
    @Output() rightButtonPressed = new EventEmitter();
    @Output() rightFeedbacksChanged = new EventEmitter<Feedback[]>();

    constructor(private jhiAlertService: JhiAlertService) {}

    ngOnInit() {
        this.jhiAlertService.clear();
        this.updateHighlightColor();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.conflictState) {
            this.updateHighlightColor();
        }
        if (changes.rightFeedback) {
            this.currentFeedbacksCopy = JSON.parse(JSON.stringify(changes.rightFeedback.currentValue));
        }
    }

    ngAfterViewInit() {
        this.setSameWidthOnModelingAssessments();
    }

    onLeftButtonPressed() {
        this.leftButtonPressed.emit();
    }

    onRightButtonPressed() {
        this.rightButtonPressed.emit();
    }

    onFeedbackChanged(feedbacks: Feedback[]) {
        this.rightFeedbacksChanged.emit(feedbacks);
    }

    setSameWidthOnModelingAssessments() {
        const conflictEditorWidth = $('#conflictEditor').width();
        const instructionsWidth = $('#assessmentInstructions').width();
        $('.resizable').css('width', (conflictEditorWidth - instructionsWidth) / 2 + 15);
    }

    private updateHighlightColor() {
        switch (this.conflictState) {
            case ConflictResolutionState.UNHANDLED:
                this.highlightColor = 'rgba(0, 123, 255, 0.6)';
                break;
            case ConflictResolutionState.ESCALATED:
                this.highlightColor = 'rgba(255, 193, 7, 0.6)';
                break;
            case ConflictResolutionState.RESOLVED:
                this.highlightColor = 'rgba(40, 167, 69, 0.6)';
                break;
        }
    }
}

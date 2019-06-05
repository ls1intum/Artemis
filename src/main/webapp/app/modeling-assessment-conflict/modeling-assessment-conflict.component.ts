import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { UMLModel } from '@ls1intum/apollon';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
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
    rightFeedbacksCopy: Feedback[];
    highlightColor: string;
    private userInteractionWithConflict = false;

    @Input() modelingExercise: ModelingExercise;
    @Input() leftTitle: string;
    @Input() leftModel: UMLModel;
    @Input() leftFeedbacks: Feedback[];
    @Input() leftHighlightedElementIds: Set<string> = new Set<string>();
    @Input() leftCenteredElementId: string;
    @Input() leftConflictingElemenId: string;
    @Input() rightTitle: string;
    @Input() rightModel: UMLModel;
    @Input() rightFeedback: Feedback[];
    @Input() rightHighlightedElementIds: Set<string> = new Set<string>();
    @Input() rightCenteredElementId: string;
    @Input() rightConflictingElemenId: string;
    @Input() rightAssessmentReadOnly = false;
    @Input() conflictState: ConflictResolutionState = ConflictResolutionState.UNHANDLED;
    @Input() resultsInConflict: ConflictingResult[];
    // @Output() escalate = new EventEmitter<{ escalatedConflicts: Conflict[]; newFeedbacks: Feedback[] }>();
    @Output() conflictResolutionStateChanged = new EventEmitter<ConflictResolutionState>();
    @Output() leftButtonPressed = new EventEmitter();
    @Output() rightButtonPressed = new EventEmitter();
    @Output() rightFeedbacksChanged = new EventEmitter<Feedback[]>();

    constructor() {}

    ngOnInit() {
        this.updateHighlightColor();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.conflictState) {
            if (changes.conflictState.currentValue === ConflictResolutionState.UNHANDLED) {
                this.userInteractionWithConflict = false;
            }
            this.updateHighlightColor();
        }
        if (changes.rightFeedback) {
            this.rightFeedbacksCopy = JSON.parse(JSON.stringify(changes.rightFeedback.currentValue));
            if (this.userInteractionWithConflict) {
                this.updateCurrentState();
            }
        }
    }

    ngAfterViewInit() {
        this.setSameWidthOnModelingAssessments();
    }

    onLeftButtonPressed() {
        this.userInteractionWithConflict = true;
        this.leftButtonPressed.emit();
    }

    onRightButtonPressed() {
        this.userInteractionWithConflict = true;
        this.rightButtonPressed.emit();
    }

    onFeedbackChanged(feedbacks: Feedback[]) {
        const elementAssessmentUpdate = feedbacks.find(feedback => feedback.referenceId === this.rightConflictingElemenId);
        const originalElementAssessment = this.rightFeedback.find(feedback => feedback.referenceId === this.rightConflictingElemenId);
        if (elementAssessmentUpdate.credits !== originalElementAssessment.credits) {
            this.userInteractionWithConflict = true;
            this.updateCurrentState();
        }
        this.rightFeedbacksChanged.emit(feedbacks);
    }

    setSameWidthOnModelingAssessments() {
        const conflictEditorWidth = $('#conflictEditor').width();
        const instructionsWidth = $('#assessmentInstructions').width();
        $('.resizable').css('width', (conflictEditorWidth - instructionsWidth) / 2 + 15);
    }

    private updateCurrentState() {
        if (this.leftConflictingElemenId && this.rightConflictingElemenId) {
            let newState;
            const rightElementFeedback = this.rightFeedback.find((feedback: Feedback) => feedback.referenceId === this.rightConflictingElemenId);
            const leftElementFeedback = this.leftFeedbacks.find((feedback: Feedback) => feedback.referenceId === this.leftConflictingElemenId);
            if (rightElementFeedback && leftElementFeedback) {
                if (rightElementFeedback.credits !== leftElementFeedback.credits) {
                    newState = ConflictResolutionState.ESCALATED;
                } else {
                    newState = ConflictResolutionState.RESOLVED;
                }
                if (newState != this.conflictState) {
                    this.conflictState = newState;
                    this.conflictResolutionStateChanged.emit(newState);
                }
            }
        }
        this.updateHighlightColor();
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

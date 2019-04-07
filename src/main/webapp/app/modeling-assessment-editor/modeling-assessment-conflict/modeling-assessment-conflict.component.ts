import { AfterViewInit, Component, OnInit } from '@angular/core';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';
import { ActivatedRoute, Router } from '@angular/router';
import { UMLModel } from '@ls1intum/apollon';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { AccountService, User } from 'app/core';
import * as $ from 'jquery';
import { ModelingAssessmentService } from 'app/modeling-assessment-editor';
import { JhiAlertService } from 'ng-jhipster';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { Feedback } from 'app/entities/feedback';
import { ConflictResolutionState } from 'app/modeling-assessment-editor/conflict-resolution-state.enum';

@Component({
    selector: 'jhi-modeling-assessment-conflict',
    templateUrl: './modeling-assessment-conflict.component.html',
    styleUrls: ['./modeling-assessment-conflict.component.scss'],
})
export class ModelingAssessmentConflictComponent implements OnInit, AfterViewInit {
    model: UMLModel;
    mergedFeedbacks: Feedback[];
    modelHighlightedElementIds: Set<string>;
    highlightColor: string;
    user: User;

    currentConflict: Conflict;
    conflictingResult: ConflictingResult;
    conflictingModel: UMLModel;
    conflictingModelHighlightedElementIds: Set<string>;
    conflicts: Conflict[];
    conflictResolutionStates: ConflictResolutionState[];
    conflictIndex = 0;
    conflictsAllHandled = false;
    modelingExercise: ModelingExercise;
    submissionId: number;

    constructor(
        private jhiAlertService: JhiAlertService,
        private route: ActivatedRoute,
        private router: Router,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private accountService: AccountService,
    ) {}

    ngOnInit() {
        this.route.params.subscribe(params => {
            this.submissionId = Number(params['submissionId']);
            this.conflicts = this.modelingAssessmentService.getLocalConflicts(this.submissionId);
            if (this.conflicts) {
                this.mergedFeedbacks = JSON.parse(JSON.stringify(this.conflicts[0].result.feedbacks));
                this.conflictResolutionStates = new Array<ConflictResolutionState>(this.conflicts.length);
                this.conflictResolutionStates.fill(ConflictResolutionState.UNHANDLED);
                this.updateSelectedConflict();
                this.model = JSON.parse((this.currentConflict.result.submission as ModelingSubmission).model);
                this.modelingExercise = this.currentConflict.result.participation.exercise as ModelingExercise;
            } else {
                this.jhiAlertService.error('modelingAssessmentEditor.messages.noConflicts');
            }
        });
        this.accountService.identity().then(value => (this.user = value));
    }

    ngAfterViewInit() {
        this.setSameWidthOnModelingAssessments();
    }

    onNextConflict() {
        this.conflictIndex = this.conflictIndex < this.conflicts.length - 1 ? ++this.conflictIndex : this.conflictIndex;
        this.updateSelectedConflict();
    }

    onPrevConflict() {
        this.conflictIndex = this.conflictIndex > 0 ? --this.conflictIndex : this.conflictIndex;
        this.updateSelectedConflict();
    }

    onKeepYours() {
        this.updateFeedbackInMergedFeedback(this.currentConflict.modelElementId, this.currentConflict.modelElementId, this.currentConflict.result.feedbacks);
        this.updateCurrentState(ConflictResolutionState.ESCALATED);
        this.updateHighlightColor();
    }

    onAcceptOther() {
        this.updateFeedbackInMergedFeedback(this.currentConflict.modelElementId, this.conflictingResult.modelElementId, this.conflictingResult.result.feedbacks);
        this.updateCurrentState(ConflictResolutionState.RESOLVED);
        this.updateHighlightColor();
    }

    onApplyChanges() {
        this.modelingAssessmentService.save(this.mergedFeedbacks, this.submissionId).subscribe(
            result => {
                this.jhiAlertService.success('modelingAssessmentEditor.messages.saveSuccessful');
                this.router.navigate(['modeling-exercise', this.modelingExercise.id, 'submissions', this.submissionId, 'assessment']);
            },
            error1 => this.jhiAlertService.error('modelingAssessmentEditor.messages.saveFailed'),
        );
    }

    updateFeedbackInMergedFeedback(elementIdToUpdate: string, elementIdToUpdateWith: string, sourceFeedbacks: Feedback[]) {
        let feedbacks: Feedback[] = [];
        const feedbackToUse = sourceFeedbacks.find((feedback: Feedback) => feedback.referenceId === elementIdToUpdateWith);
        this.mergedFeedbacks.forEach(feedback => {
            if (feedback.referenceId === elementIdToUpdate) {
                feedback.credits = feedbackToUse.credits;
            }
            feedbacks.push(feedback);
        });
        this.mergedFeedbacks = feedbacks;
    }

    setSameWidthOnModelingAssessments() {
        const conflictEditorWidth = $('#conflictEditor').width();
        const instructionsWidth = $('#assessmentInstructions').width();
        $('.resizable').css('width', (conflictEditorWidth - instructionsWidth) / 2 + 15);
    }

    private updateSelectedConflict() {
        this.currentConflict = this.conflicts[this.conflictIndex];
        this.conflictingResult = this.currentConflict.conflictingResults[0];
        this.conflictingModel = JSON.parse((this.conflictingResult.result.submission as ModelingSubmission).model);
        this.updateHighlightedElements();
        this.updateHighlightColor();
    }

    private updateHighlightedElements() {
        this.modelHighlightedElementIds = new Set<string>([this.currentConflict.modelElementId]);
        this.conflictingModelHighlightedElementIds = new Set<string>([this.conflictingResult.modelElementId]);
    }

    private updateHighlightColor() {
        switch (this.conflictResolutionStates[this.conflictIndex]) {
            case ConflictResolutionState.UNHANDLED:
                // this.highlightColor = 'rgba(219, 53, 69, 0.6)';
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

    private updateCurrentState(newState: ConflictResolutionState) {
        this.conflictResolutionStates[this.conflictIndex] = newState;
        this.updateOverallResolutioState();
    }

    private updateOverallResolutioState() {
        for (const state of this.conflictResolutionStates) {
            if (state === ConflictResolutionState.UNHANDLED) {
                this.conflictsAllHandled = false;
                return;
            }
        }
        if (!this.conflictsAllHandled) {
            this.jhiAlertService.success('modelingAssessmentEditor.messages.conflictsResolved');
        }
        this.conflictsAllHandled = true;
    }

    private updateCenteredElement() {}
}

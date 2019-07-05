import { Component, OnInit } from '@angular/core';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { ConflictResolutionState } from 'app/modeling-assessment-editor/conflict-resolution-state.enum';
import { Feedback } from 'app/entities/feedback';
import { ModelingSubmission } from 'app/entities/modeling-submission';
import { AccountService } from 'app/core';
import { UMLModel } from '@ls1intum/apollon';
import { ModelingAssessmentConflictService } from 'app/modeling-assessment-conflict/modeling-assessment-conflict.service';
import { JhiAlertService } from 'ng-jhipster';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
    selector: 'jhi-instructor-conflict-resolution',
    templateUrl: './instructor-conflict-resolution.component.html',
    styles: [],
})
export class InstructorConflictResolutionComponent implements OnInit {
    // private conflictId: number;
    private conflicts: Conflict[];
    modelingExercise: ModelingExercise;

    conflictResolutionStates: ConflictResolutionState[];

    currentState: ConflictResolutionState;
    conflictIndex = 0;

    currentConflict: Conflict | undefined;
    currentUserId: number | null;
    rightModel: UMLModel;
    rightConflictingResult: ConflictingResult | undefined;
    currentHighlightedElementIds: Set<string>;
    currentCenteredElementId: string;
    currentFeedbacksCopy: Feedback[];

    leftConflictingResult: ConflictingResult;
    leftModel: UMLModel;
    conflictingHighlightedElementIds: Set<string>;
    conflictingCenteredElementId: string;

    constructor(
        private route: ActivatedRoute,
        private conflictService: ModelingAssessmentConflictService,
        private jhiAlertService: JhiAlertService,
        private router: Router,
        private accountService: AccountService,
    ) {}

    ngOnInit() {
        this.route.params.subscribe(params => {
            const conflictId = Number(params['conflictId']);
            this.conflictService.getConflictsForResultInConflict(conflictId).subscribe(
                conflicts => {
                    this.conflicts = conflicts;
                    this.accountService.identity().then(user => {
                        this.currentUserId = user ? user.id : null;
                        this.initComponent();
                    });
                },
                error => {
                    this.jhiAlertService.error('modelingAssessmentConflict.messages.noConflicts');
                },
            );
        });
    }

    initComponent() {
        this.initResolutionStates(this.conflicts);
        this.onCurrentConflictChanged(0);
        if (this.currentConflict && this.rightConflictingResult) {
            this.currentFeedbacksCopy = JSON.parse(JSON.stringify(this.rightConflictingResult.result!.feedbacks));
            this.rightModel = JSON.parse((this.rightConflictingResult.result.submission as ModelingSubmission).model);
            this.modelingExercise = this.currentConflict.causingConflictingResult.result.participation!.exercise as ModelingExercise;
        }
    }

    onCurrentConflictChanged(conflictIndex: number) {
        this.conflictIndex = conflictIndex;
        this.currentConflict = this.conflicts[conflictIndex];
        if (this.currentConflict) {
            this.rightConflictingResult = this.getUsersConflictingResult();
            if (this.conflictResolutionStates) {
                this.currentState = this.conflictResolutionStates[conflictIndex];
            }
            this.leftConflictingResult = this.currentConflict.causingConflictingResult;
            this.leftModel = JSON.parse((this.currentConflict.causingConflictingResult.result.submission as ModelingSubmission).model);
            this.updateHighlightedElements();
            this.updateCenteredElements();
        }
    }

    onSave() {}

    onSubmit(escalatedConflicts: Conflict[]) {}

    onKeepYours() {
        if (this.rightConflictingResult) {
            this.updateFeedbackInMergedFeedback(
                this.rightConflictingResult.modelElementId,
                this.rightConflictingResult.modelElementId,
                this.rightConflictingResult.result.feedbacks,
            );
        }
    }

    onTakeOver() {
        if (this.rightConflictingResult) {
            this.updateFeedbackInMergedFeedback(this.rightConflictingResult.modelElementId, this.leftConflictingResult.modelElementId, this.leftConflictingResult.result.feedbacks);
        }
    }

    onFeedbackChanged(feedbacks: Feedback[]) {
        // this.mergedFeedbacks = feedbacks;
    }

    onConflictStateChanged(newState: ConflictResolutionState) {
        this.conflictResolutionStates[this.conflictIndex] = newState;
        this.currentState = newState;
        this.conflictResolutionStates = JSON.parse(JSON.stringify(this.conflictResolutionStates));
    }

    private initResolutionStates(conflicts: Conflict[]) {
        // TODO MJ move into service
        this.conflictResolutionStates = [];
        if (conflicts && conflicts.length > 0) {
            const mergedFeedbacks = conflicts[0].causingConflictingResult!.result.feedbacks;
            for (let i = 0; i < this.conflicts.length; i++) {
                const currentConflict: Conflict = conflicts[i];
                const conflictingResult: ConflictingResult = currentConflict.resultsInConflict[0];
                const mergedFeedback = mergedFeedbacks.find((feedback: Feedback) => feedback.referenceId === currentConflict.causingConflictingResult.modelElementId);
                const conflictingFeedback = conflictingResult.result.feedbacks.find((feedback: Feedback) => feedback.referenceId === conflictingResult.modelElementId);
                if (mergedFeedback && conflictingFeedback && mergedFeedback.credits !== conflictingFeedback.credits) {
                    this.conflictResolutionStates.push(ConflictResolutionState.UNHANDLED);
                } else {
                    this.conflictResolutionStates.push(ConflictResolutionState.RESOLVED);
                }
            }
        }
        this.currentState = this.conflictResolutionStates[this.conflictIndex];
    }

    private updateFeedbackInMergedFeedback(elementIdToUpdate: string, elementIdToUpdateWith: string, sourceFeedbacks: Feedback[]) {
        const feedbacks: Feedback[] = [];
        const feedbackToUse = sourceFeedbacks.find((feedback: Feedback) => feedback.referenceId === elementIdToUpdateWith);
        if (feedbackToUse) {
            this.currentFeedbacksCopy.forEach(feedback => {
                if (feedback.referenceId === elementIdToUpdate) {
                    feedback.credits = feedbackToUse.credits;
                }
                feedbacks.push(feedback);
            });
            this.currentFeedbacksCopy = feedbacks;
        }
    }

    private updateHighlightedElements() {
        if (this.rightConflictingResult) {
            this.currentHighlightedElementIds = new Set<string>([this.rightConflictingResult.modelElementId]);
        }
        this.conflictingHighlightedElementIds = new Set<string>([this.leftConflictingResult.modelElementId]);
    }

    private updateCenteredElements() {
        this.currentCenteredElementId = this.currentHighlightedElementIds.values().next().value;
        this.conflictingCenteredElementId = this.conflictingHighlightedElementIds.values().next().value;
    }

    private getUsersConflictingResult(): ConflictingResult | undefined {
        if (this.currentConflict) {
            return this.currentConflict.resultsInConflict.find((conflictingResult: ConflictingResult) => conflictingResult.result.assessor.id === this.currentUserId);
        } else {
            return undefined;
        }
    }
}

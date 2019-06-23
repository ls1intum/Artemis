import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { ActivatedRoute, Router } from '@angular/router';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Feedback } from 'app/entities/feedback';
import { AccountService } from 'app/core';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { ConflictResolutionState } from 'app/modeling-assessment-editor/conflict-resolution-state.enum';
import { UMLModel } from '@ls1intum/apollon';
import { ConflictEscalationModalComponent } from 'app/modeling-assessment-conflict/conflict-escalation-modal/conflict-escalation-modal.component';

@Component({
    selector: 'jhi-escalated-conflict-resolution',
    templateUrl: './escalated-conflict-resolution.component.html',
    styles: [],
})
export class EscalatedConflictResolutionComponent implements OnInit {
    private resultId: number;
    private conflicts: Conflict[];
    modelingExercise: ModelingExercise;

    conflictResolutionStates: ConflictResolutionState[];

    currentState: ConflictResolutionState;
    conflictIndex = 0;

    currentConflict: Conflict | undefined;
    currentModel: UMLModel;
    currentUserId: number | null;
    currentConflictingResult: ConflictingResult | undefined;
    currentHighlightedElementIds: Set<string>;
    currentCenteredElementId: string;
    currentFeedbacksCopy: Feedback[];

    conflictingResult: ConflictingResult;
    conflictingModel: UMLModel;
    conflictingHighlightedElementIds: Set<string>;
    conflictingCenteredElementId: string;

    @ViewChild('escalationModal', { static: false }) escalationModal: ElementRef;

    constructor(
        private route: ActivatedRoute,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private jhiAlertService: JhiAlertService,
        private router: Router,
        private modalService: NgbModal,
        private accountService: AccountService,
    ) {}

    ngOnInit() {
        this.route.params.subscribe(params => {
            this.resultId = Number(params['resultId']);
            this.modelingAssessmentService.getConflictsForResultInConflict(this.resultId).subscribe(
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
        this.onCurrentConflictChanged(0);
        if (this.currentConflict && this.currentConflictingResult) {
            this.currentFeedbacksCopy = JSON.parse(JSON.stringify(this.currentConflictingResult.result!.feedbacks));
            this.currentModel = JSON.parse((this.currentConflictingResult.result.submission as ModelingSubmission).model);
            this.modelingExercise = this.currentConflict.causingConflictingResult.result.participation!.exercise as ModelingExercise;
            this.initResolutionStates(this.conflicts);
        }
    }

    onCurrentConflictChanged(conflictIndex: number) {
        this.conflictIndex = conflictIndex;
        this.currentConflict = this.conflicts[conflictIndex];
        if (this.currentConflict) {
            this.currentConflictingResult = this.getUsersConflictingResult();
            if (this.conflictResolutionStates) {
                this.currentState = this.conflictResolutionStates[conflictIndex];
            }
            this.conflictingResult = this.currentConflict.causingConflictingResult;
            this.conflictingModel = JSON.parse((this.currentConflict.causingConflictingResult.result.submission as ModelingSubmission).model);
            this.updateHighlightedElements();
            this.updateCenteredElements();
        }
    }

    onSave() {}

    onSubmit(escalatedConflicts: Conflict[]) {
        if (escalatedConflicts && escalatedConflicts.length > 0) {
            this.escalateAndSubmit(escalatedConflicts);
        } else {
            this.submit();
        }
    }

    onKeepYours() {
        if (this.currentConflictingResult) {
            this.updateFeedbackInMergedFeedback(
                this.currentConflictingResult.modelElementId,
                this.currentConflictingResult.modelElementId,
                this.currentConflictingResult.result.feedbacks,
            );
        }
    }

    onTakeOver() {
        if (this.currentConflictingResult) {
            this.updateFeedbackInMergedFeedback(this.currentConflictingResult.modelElementId, this.conflictingResult.modelElementId, this.conflictingResult.result.feedbacks);
        }
    }

    escalateAndSubmit(escalatedConflicts: Conflict[]) {
        const modalRef = this.modalService.open(ConflictEscalationModalComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.escalatedConflictsCount = escalatedConflicts.length;
        modalRef.result.then(() => {
            this.modelingAssessmentService.escalateConflict(escalatedConflicts).subscribe(() => this.submit());
        });
    }

    onFeedbackChanged(feedbacks: Feedback[]) {
        // this.mergedFeedbacks = feedbacks;
    }

    onConflictStateChanged(newState: ConflictResolutionState) {
        this.conflictResolutionStates[this.conflictIndex] = newState;
        this.currentState = newState;
        this.conflictResolutionStates = JSON.parse(JSON.stringify(this.conflictResolutionStates));
    }

    private submit() {
        // this.modelingAssessmentService.updateConflicts();
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
        if (this.currentConflictingResult) {
            this.currentHighlightedElementIds = new Set<string>([this.currentConflictingResult.modelElementId]);
        }
        this.conflictingHighlightedElementIds = new Set<string>([this.conflictingResult.modelElementId]);
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

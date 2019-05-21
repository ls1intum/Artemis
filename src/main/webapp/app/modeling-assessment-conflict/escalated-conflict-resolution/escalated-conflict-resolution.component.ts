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

    currentConflict: Conflict;
    currentModel: UMLModel;
    currentUserId: number;
    currentConflictingResult: ConflictingResult;
    currentHighlightedElementIds: Set<string>;
    currentCenteredElementId: string;
    currentFeedbacksCopy: Feedback[];

    conflictingResult: ConflictingResult;
    conflictingModel: UMLModel;
    conflictingHighlightedElementIds: Set<string>;
    conflictingCenteredElementId: string;

    @ViewChild('escalationModal') escalationModal: ElementRef;

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
                        this.currentUserId = user.id;
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
        this.currentFeedbacksCopy = JSON.parse(JSON.stringify(this.conflicts[this.conflictIndex].causingConflictingResult.result.feedbacks));
        this.initResolutionStates(this.conflicts);
        this.onCurrentConflictChanged(0);
        this.modelingExercise = this.currentConflict.causingConflictingResult.result.participation.exercise as ModelingExercise;
        this.currentModel = JSON.parse((this.conflictingResult.result.submission as ModelingSubmission).model);
    }

    onCurrentConflictChanged(conflictIndex: number) {
        this.conflictIndex = conflictIndex;
        this.currentConflict = this.conflicts[conflictIndex];
        this.currentConflictingResult = this.getUsersConflictingResult();
        this.currentState = this.conflictResolutionStates[conflictIndex];
        this.conflictingResult = this.currentConflict.causingConflictingResult;
        this.conflictingModel = JSON.parse((this.conflictingResult.result.submission as ModelingSubmission).model);
        this.updateHighlightedElements();
        this.currentCenteredElementId = this.currentHighlightedElementIds.values().next().value;
        this.conflictingCenteredElementId = this.conflictingHighlightedElementIds.values().next().value;
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
        this.updateFeedbackInMergedFeedback(
            this.currentConflict.causingConflictingResult.modelElementId,
            this.currentConflict.causingConflictingResult.modelElementId,
            this.currentConflict.causingConflictingResult.result.feedbacks,
        );
    }

    onTakeOver() {
        this.updateFeedbackInMergedFeedback(
            this.currentConflict.causingConflictingResult.modelElementId,
            this.conflictingResult.modelElementId,
            this.conflictingResult.result.feedbacks,
        );
    }

    escalateAndSubmit(escalatedConflicts: Conflict[]) {
        const modalRef = this.modalService.open(ConflictEscalationModalComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.tutorsEscalatingTo = this.getDistinctTutorsEscalatingTo(escalatedConflicts);
        modalRef.componentInstance.escalatedConflictsCount = escalatedConflicts.length;
        modalRef.result.then(() => {
            this.modelingAssessmentService.escalateConflict(escalatedConflicts).subscribe(() => this.submit());
        });
    }

    onFeedbackChanged(feedbacks: Feedback[]) {
        this.mergedFeedbacks = feedbacks;
    }

    onConflictStateChanged(newState: ConflictResolutionState) {
        this.conflictResolutionStates[this.conflictIndex] = newState;
        this.currentState = newState;
        this.conflictResolutionStates = JSON.parse(JSON.stringify(this.conflictResolutionStates));
    }

    private submit() {}

    private initResolutionStates(conflicts: Conflict[]) {
        //TODO MJ move into service
        this.conflictResolutionStates = [];
        if (conflicts && conflicts.length > 0) {
            const mergedFeedbacks = conflicts[0].causingConflictingResult.result.feedbacks;
            for (let i = 0; i < this.conflicts.length; i++) {
                const currentConflict: Conflict = conflicts[i];
                const conflictingResult: ConflictingResult = currentConflict.resultsInConflict[0];
                const mergedFeedback = mergedFeedbacks.find((feedback: Feedback) => feedback.referenceId === currentConflict.causingConflictingResult.modelElementId);
                const conflictingFeedback = conflictingResult.result.feedbacks.find((feedback: Feedback) => feedback.referenceId === conflictingResult.modelElementId);
                if (mergedFeedback.credits !== conflictingFeedback.credits) {
                    this.conflictResolutionStates.push(ConflictResolutionState.UNHANDLED);
                } else {
                    this.conflictResolutionStates.push(ConflictResolutionState.RESOLVED);
                }
            }
        }
    }

    private updateHighlightedElements() {
        this.currentHighlightedElementIds = new Set<string>([this.currentConflict.causingConflictingResult.modelElementId]);
        this.conflictingHighlightedElementIds = new Set<string>([this.conflictingResult.modelElementId]);
    }

    private getUsersConflictingResult(): ConflictingResult {
        return this.currentConflict.resultsInConflict.find((conflictingResult: ConflictingResult) => conflictingResult.result.assessor.id === this.currentUserId);
    }
}

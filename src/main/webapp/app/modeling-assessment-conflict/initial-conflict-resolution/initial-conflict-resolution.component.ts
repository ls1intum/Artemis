import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { JhiAlertService } from 'ng-jhipster';
import { Feedback } from 'app/entities/feedback';
import { ConflictEscalationModalComponent } from 'app/modeling-assessment-conflict/conflict-escalation-modal/conflict-escalation-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { User } from 'app/core';
import { ConflictResolutionState } from 'app/modeling-assessment-editor/conflict-resolution-state.enum';
import { UMLModel } from '@ls1intum/apollon';
import { ModelingExercise } from 'app/entities/modeling-exercise';

@Component({
    selector: 'jhi-initial-conflict-resolution',
    templateUrl: './initial-conflict-resolution.component.html',
    styles: [],
})
export class InitialConflictResolutionComponent implements OnInit {
    private submissionId: number;
    private conflicts: Conflict[];
    modelingExercise: ModelingExercise;

    conflictResolutionStates: ConflictResolutionState[];
    conflictsAllHandled = false;
    currentState: ConflictResolutionState;
    conflictIndex = 0;

    currentConflict: Conflict;
    currentModel: UMLModel;
    currentHighlightedElementIds: Set<string>;
    currentCenteredElementId: string;
    mergedFeedbacks: Feedback[];

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
    ) {}

    ngOnInit() {
        this.route.params.subscribe(params => {
            this.submissionId = Number(params['submissionId']);
            this.conflicts = this.modelingAssessmentService.popLocalConflicts(this.submissionId);
            if (this.conflicts && this.conflicts.length > 0) {
                this.initComponent();
            } else {
                this.modelingAssessmentService.getConflicts(this.submissionId).subscribe(
                    conflicts => {
                        this.conflicts = conflicts;
                        this.initComponent();
                    },
                    error => {
                        this.jhiAlertService.error('modelingAssessmentConflict.messages.noConflicts');
                    },
                );
            }
        });
    }
    initComponent() {
        this.mergedFeedbacks = JSON.parse(JSON.stringify(this.conflicts[this.conflictIndex].causingConflictingResult.result.feedbacks));
        this.initResolutionStates();
        this.onCurrentConflictChanged(0);
        this.modelingExercise = this.currentConflict.causingConflictingResult.result.participation.exercise as ModelingExercise;
        this.currentModel = JSON.parse((this.currentConflict.causingConflictingResult.result.submission as ModelingSubmission).model);
    }

    onSave(newFeedbacks: Feedback[]) {
        this.modelingAssessmentService.saveAssessment(newFeedbacks, this.submissionId).subscribe(
            () => {
                this.jhiAlertService.success('modelingAssessmentEditor.messages.saveSuccessful');
            },
            () => this.jhiAlertService.error('modelingAssessmentEditor.messages.saveFailed'),
        );
    }

    onKeepYours() {
        this.updateFeedbackInMergedFeedback(
            this.currentConflict.causingConflictingResult.modelElementId,
            this.currentConflict.causingConflictingResult.modelElementId,
            this.currentConflict.causingConflictingResult.result.feedbacks,
        );
        // this.currentConflict.causingConflictingResult.result.feedbacks = JSON.parse(JSON.stringify(this.mergedFeedbacks));
        this.updateCurrentState();
    }

    onTakeOver() {
        this.updateFeedbackInMergedFeedback(
            this.currentConflict.causingConflictingResult.modelElementId,
            this.conflictingResult.modelElementId,
            this.conflictingResult.result.feedbacks,
        );
        // this.currentConflict.causingConflictingResult.result.feedbacks = JSON.parse(JSON.stringify(this.mergedFeedbacks));
        this.updateCurrentState();
    }

    onEscalate(emitted: { escalatedConflicts: Conflict[]; newFeedbacks: Feedback[] }) {
        const modalRef = this.modalService.open(ConflictEscalationModalComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.tutorsEscalatingTo = this.getDistinctTutorsEscalatingTo(emitted.escalatedConflicts);
        modalRef.componentInstance.escalatedConflictsCount = emitted.escalatedConflicts.length;
        modalRef.result.then(value => {
            this.modelingAssessmentService.escalateConflict(emitted.escalatedConflicts).subscribe(() => this.onSubmit(emitted.newFeedbacks));
        });
    }

    onSubmit(newFeedbacks: Feedback[]) {
        this.modelingAssessmentService.saveAssessment(newFeedbacks, this.submissionId, true).subscribe(
            () => {
                this.jhiAlertService.success('modelingAssessmentEditor.messages.submitSuccessful');
                this.router.navigate([
                    'modeling-exercise',
                    this.conflicts[0].causingConflictingResult.result.participation.exercise.id,
                    'submissions',
                    this.submissionId,
                    'assessment',
                ]);
            },
            error => {
                if (error.status === 409) {
                    const conflicts = error.error as Conflict[];
                    this.modelingAssessmentService.convertConflicts(conflicts);
                    this.conflicts = conflicts;
                    this.jhiAlertService.clear();
                    this.jhiAlertService.error('modelingAssessmentEditor.messages.submitFailedWithConflict');
                } else {
                    this.jhiAlertService.clear();
                    this.jhiAlertService.error('modelingAssessmentEditor.messages.submitFailed');
                }
            },
        );
    }

    onCurrentConflictChanged(conflictIndex: number) {
        this.conflictIndex = conflictIndex;
        this.currentConflict = this.conflicts[conflictIndex];
        this.currentState = this.conflictResolutionStates[conflictIndex];
        this.conflictingResult = this.currentConflict.resultsInConflict[0];
        this.conflictingModel = JSON.parse((this.conflictingResult.result.submission as ModelingSubmission).model);
        this.updateHighlightedElements();
        this.currentCenteredElementId = this.currentHighlightedElementIds.values().next().value;
        this.conflictingCenteredElementId = this.conflictingHighlightedElementIds.values().next().value;
    }

    onFeedbackChanged(feedbacks: Feedback[]) {
        const elementAssessmentUpdate = feedbacks.find(feedback => feedback.referenceId === this.currentConflict.causingConflictingResult.modelElementId);
        const originalElementAssessment = this.currentConflict.causingConflictingResult.result.feedbacks.find(
            feedback => feedback.referenceId === this.currentConflict.causingConflictingResult.modelElementId,
        );
        if (elementAssessmentUpdate.credits !== originalElementAssessment.credits) {
            this.updateCurrentState();
        }
        this.mergedFeedbacks = feedbacks;
    }

    updateFeedbackInMergedFeedback(elementIdToUpdate: string, elementIdToUpdateWith: string, sourceFeedbacks: Feedback[]) {
        const feedbacks: Feedback[] = [];
        const feedbackToUse = sourceFeedbacks.find((feedback: Feedback) => feedback.referenceId === elementIdToUpdateWith);
        this.mergedFeedbacks.forEach(feedback => {
            if (feedback.referenceId === elementIdToUpdate) {
                feedback.credits = feedbackToUse.credits;
            }
            feedbacks.push(feedback);
        });
        this.mergedFeedbacks = feedbacks;
    }

    private updateHighlightedElements() {
        this.currentHighlightedElementIds = new Set<string>([this.currentConflict.causingConflictingResult.modelElementId]);
        this.conflictingHighlightedElementIds = new Set<string>([this.conflictingResult.modelElementId]);
    }

    private updateCurrentState() {
        const mergedFeedback = this.mergedFeedbacks.find((feedback: Feedback) => feedback.referenceId === this.currentConflict.causingConflictingResult.modelElementId);
        const conflictingFeedback = this.conflictingResult.result.feedbacks.find((feedback: Feedback) => feedback.referenceId === this.conflictingResult.modelElementId);
        if (mergedFeedback.credits !== conflictingFeedback.credits) {
            this.conflictResolutionStates[this.conflictIndex] = ConflictResolutionState.ESCALATED;
        } else {
            this.conflictResolutionStates[this.conflictIndex] = ConflictResolutionState.RESOLVED;
        }
        this.currentState = this.conflictResolutionStates[this.conflictIndex];
        this.updateOverallResolutionState();
    }

    private initResolutionStates() {
        this.conflictResolutionStates = [];
        for (let i = 0; i < this.conflicts.length; i++) {
            const currentConflict: Conflict = this.conflicts[i];
            const conflictingResult: ConflictingResult = currentConflict.resultsInConflict[0];
            const mergedFeedback = this.mergedFeedbacks.find((feedback: Feedback) => feedback.referenceId === currentConflict.causingConflictingResult.modelElementId);
            const conflictingFeedback = conflictingResult.result.feedbacks.find((feedback: Feedback) => feedback.referenceId === conflictingResult.modelElementId);
            if (mergedFeedback.credits !== conflictingFeedback.credits) {
                this.conflictResolutionStates.push(ConflictResolutionState.UNHANDLED);
            } else {
                this.conflictResolutionStates.push(ConflictResolutionState.RESOLVED);
            }
        }
    }

    private updateOverallResolutionState() {
        for (const state of this.conflictResolutionStates) {
            if (state === ConflictResolutionState.UNHANDLED) {
                this.conflictsAllHandled = false;
                return;
            }
        }
        this.conflictsAllHandled = true;
        this.jhiAlertService.success('modelingAssessmentConflict.messages.conflictsResolved');
    }

    private getEscalatedConflicts(): Conflict[] {
        const escalatedConflicts: Conflict[] = [];
        for (let i = 0; i < this.conflictResolutionStates.length; i++) {
            if (this.conflictResolutionStates[i] === ConflictResolutionState.ESCALATED) {
                escalatedConflicts.push(this.conflicts[i]);
            }
        }
        return escalatedConflicts;
    }

    private getDistinctTutorsEscalatingTo(escalatedConflicts: Conflict[]): User[] {
        const distinctTutors: Map<number, User> = new Map<number, User>();
        escalatedConflicts.forEach((conflict: Conflict) => {
            conflict.resultsInConflict.forEach((conflictingResult: ConflictingResult) =>
                distinctTutors.set(conflictingResult.result.assessor.id, conflictingResult.result.assessor),
            );
        });
        return Array.from(distinctTutors.values());
    }
}

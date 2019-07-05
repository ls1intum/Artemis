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
import { ModelingAssessmentConflictService } from 'app/modeling-assessment-conflict/modeling-assessment-conflict.service';

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

    currentState: ConflictResolutionState;
    conflictIndex = 0;

    currentConflict: Conflict | undefined;
    currentModel: UMLModel;
    currentHighlightedElementIds: Set<string>;
    currentCenteredElementId: string;
    mergedFeedbacks: Feedback[];

    conflictingResult: ConflictingResult;
    conflictingModel: UMLModel;
    conflictingHighlightedElementIds: Set<string>;
    conflictingCenteredElementId: string;

    @ViewChild('escalationModal', { static: false }) escalationModal: ElementRef;

    constructor(
        private route: ActivatedRoute,
        private modelingAssessmentService: ModelingAssessmentService,
        private conflictService: ModelingAssessmentConflictService,
        private jhiAlertService: JhiAlertService,
        private router: Router,
        private modalService: NgbModal,
    ) {}

    ngOnInit() {
        this.route.params.subscribe(params => {
            this.submissionId = Number(params['submissionId']);
            this.conflicts = this.conflictService.popLocalConflicts(this.submissionId);
            if (this.conflicts.length > 0) {
                this.initComponent();
            } else {
                this.conflictService.getConflictsForSubmission(this.submissionId).subscribe(
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
        this.initResolutionStates(this.conflicts);
        this.onCurrentConflictChanged(0);
        if (this.currentConflict) {
            this.mergedFeedbacks = JSON.parse(JSON.stringify(this.conflicts[this.conflictIndex].causingConflictingResult.result.feedbacks));
            this.modelingExercise = this.currentConflict.causingConflictingResult.result.participation!.exercise as ModelingExercise;
            this.currentModel = JSON.parse((this.currentConflict.causingConflictingResult.result.submission as ModelingSubmission).model);
            this.jhiAlertService.clear();
            this.jhiAlertService.addAlert(
                {
                    type: 'info',
                    msg: 'modelingAssessmentConflict.messages.conflictResolutionInstructions',
                    timeout: undefined,
                },
                [],
            );
        }
    }

    onSave() {
        this.modelingAssessmentService.saveAssessment(this.mergedFeedbacks, this.submissionId).subscribe(
            () => {
                this.jhiAlertService.success('modelingAssessmentEditor.messages.saveSuccessful');
            },
            () => this.jhiAlertService.error('modelingAssessmentEditor.messages.saveFailed'),
        );
    }

    onKeepYours() {
        if (this.currentConflict) {
            this.updateFeedbackInMergedFeedback(
                this.currentConflict.causingConflictingResult.modelElementId,
                this.currentConflict.causingConflictingResult.modelElementId,
                this.currentConflict.causingConflictingResult.result.feedbacks,
            );
        }
    }

    onTakeOver() {
        if (this.currentConflict) {
            this.updateFeedbackInMergedFeedback(
                this.currentConflict.causingConflictingResult.modelElementId,
                this.conflictingResult.modelElementId,
                this.conflictingResult.result.feedbacks,
            );
        }
    }

    onFeedbackChanged(feedbacks: Feedback[]) {
        this.mergedFeedbacks = feedbacks;
    }

    onConflictStateChanged(newState: ConflictResolutionState) {
        this.conflictResolutionStates[this.conflictIndex] = newState;
        this.currentState = newState;
        // this.conflictResolutionStates = [...this.conflictResolutionStates];
    }

    onSubmit(escalatedConflicts: Conflict[]) {
        if (escalatedConflicts && escalatedConflicts.length > 0) {
            this.escalateAndSubmit(escalatedConflicts);
        } else {
            this.submit();
        }
    }

    onCurrentConflictChanged(conflictIndex: number) {
        this.conflictIndex = conflictIndex;
        this.currentConflict = this.conflicts[conflictIndex];
        this.onConflictStateChanged(this.conflictResolutionStates[conflictIndex]);
        this.conflictingResult = this.currentConflict.resultsInConflict[0];
        this.conflictingModel = JSON.parse((this.conflictingResult.result.submission as ModelingSubmission).model);
        this.updateHighlightedElements();
        this.currentCenteredElementId = this.currentHighlightedElementIds.values().next().value;
        this.conflictingCenteredElementId = this.conflictingHighlightedElementIds.values().next().value;
    }

    escalateAndSubmit(escalatedConflicts: Conflict[]) {
        const modalRef = this.modalService.open(ConflictEscalationModalComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.tutorsEscalatingTo = this.getDistinctTutorsEscalatingTo(escalatedConflicts);
        modalRef.componentInstance.escalatedConflictsCount = escalatedConflicts.length;
        modalRef.result.then(() => {
            this.conflictService.escalateConflict(escalatedConflicts).subscribe(() => this.submit());
        });
    }

    submit() {
        this.modelingAssessmentService.saveAssessment(this.mergedFeedbacks, this.submissionId, true).subscribe(
            () => {
                this.jhiAlertService.success('modelingAssessmentEditor.messages.submitSuccessful');
                this.router.navigate(['modeling-exercise', this.modelingExercise.id, 'submissions', this.submissionId, 'assessment']);
            },
            error => {
                if (error.status === 409) {
                    const conflicts = error.error as Conflict[];
                    this.conflictService.convertConflicts(conflicts);
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

    private updateFeedbackInMergedFeedback(elementIdToUpdate: string, elementIdToUpdateWith: string, sourceFeedbacks: Feedback[]) {
        const feedbacks: Feedback[] = [];
        const feedbackToUse = sourceFeedbacks.find((feedback: Feedback) => feedback.referenceId === elementIdToUpdateWith);
        if (feedbackToUse) {
            this.mergedFeedbacks.forEach(feedback => {
                if (feedback.referenceId === elementIdToUpdate) {
                    feedback.credits = feedbackToUse.credits;
                }
                feedbacks.push(feedback);
            });
            this.mergedFeedbacks = feedbacks;
        }
    }

    private updateHighlightedElements() {
        if (this.currentConflict) {
            this.currentHighlightedElementIds = new Set<string>([this.currentConflict.causingConflictingResult.modelElementId]);
        }
        this.conflictingHighlightedElementIds = new Set<string>([this.conflictingResult.modelElementId]);
    }

    private initResolutionStates(conflicts: Conflict[]) {
        // TODO MJ move into service
        this.conflictResolutionStates = [];
        if (conflicts && conflicts.length > 0) {
            const mergedFeedbacks = conflicts[0].causingConflictingResult.result.feedbacks;
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
    }

    private getDistinctTutorsEscalatingTo(escalatedConflicts: Conflict[]): User[] {
        const distinctTutors: Map<number, User> = new Map<number, User>();
        escalatedConflicts.forEach((conflict: Conflict) => {
            conflict.resultsInConflict.forEach((conflictingResult: ConflictingResult) =>
                distinctTutors.set(conflictingResult.result.assessor.id!, conflictingResult.result.assessor),
            );
        });
        return Array.from(distinctTutors.values());
    }
}

import { AfterViewInit, Component, OnInit, ElementRef, ViewChild } from '@angular/core';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';
import { ActivatedRoute, Router } from '@angular/router';
import { UMLModel } from '@ls1intum/apollon';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { AccountService, User } from 'app/core';
import * as $ from 'jquery';
import { JhiAlertService } from 'ng-jhipster';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { Feedback } from 'app/entities/feedback';
import { ConflictResolutionState } from 'app/modeling-assessment-editor/conflict-resolution-state.enum';
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ConflictEscalationModalComponent } from 'app/modeling-assessment-conflict/conflict-escalation-modal/conflict-escalation-modal.component';

@Component({
    selector: 'jhi-modeling-assessment-conflict',
    templateUrl: './modeling-assessment-conflict.component.html',
    styleUrls: ['./modeling-assessment-conflict.component.scss', '../modeling-assessment-editor/modeling-assessment-editor.component.scss'],
})
export class ModelingAssessmentConflictComponent implements OnInit, AfterViewInit {
    model: UMLModel;
    mergedFeedbacks: Feedback[];
    currentFeedbacksCopy: Feedback[];
    modelHighlightedElementIds: Set<string>;
    highlightColor: string;
    // user: User;

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

    @ViewChild('escalationModal') escalationModal: ElementRef;
    constructor(
        private jhiAlertService: JhiAlertService,
        private route: ActivatedRoute,
        private router: Router,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private accountService: AccountService,
        private modalService: NgbModal,
    ) {}

    ngOnInit() {
        this.jhiAlertService.clear();
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

    ngAfterViewInit() {
        this.setSameWidthOnModelingAssessments();
    }

    initComponent() {
        this.jhiAlertService.clear();
        this.mergedFeedbacks = JSON.parse(JSON.stringify(this.conflicts[0].causingConflictingResult.result.feedbacks));
        this.currentFeedbacksCopy = JSON.parse(JSON.stringify(this.conflicts[0].causingConflictingResult.result.feedbacks));
        this.initResolutionStates();
        this.updateOverallResolutionState();
        if (!this.conflictsAllHandled) {
            this.jhiAlertService.addAlert({ type: 'info', msg: 'modelingAssessmentConflict.messages.conflictResolutionInstructions', timeout: 15000 }, []);
        }
        this.updateSelectedConflict();
        this.model = JSON.parse((this.currentConflict.causingConflictingResult.result.submission as ModelingSubmission).model);
        this.modelingExercise = this.currentConflict.causingConflictingResult.result.participation.exercise as ModelingExercise;
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
        this.updateFeedbackInMergedFeedback(
            this.currentConflict.causingConflictingResult.modelElementId,
            this.currentConflict.causingConflictingResult.modelElementId,
            this.currentConflict.causingConflictingResult.result.feedbacks,
        );
        this.currentFeedbacksCopy = JSON.parse(JSON.stringify(this.mergedFeedbacks));
        this.updateCurrentState();
    }

    onTakeOver() {
        this.updateFeedbackInMergedFeedback(
            this.currentConflict.causingConflictingResult.modelElementId,
            this.conflictingResult.modelElementId,
            this.conflictingResult.result.feedbacks,
        );
        this.currentFeedbacksCopy = JSON.parse(JSON.stringify(this.mergedFeedbacks));
        this.updateCurrentState();
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

    onSave() {
        this.modelingAssessmentService.saveAssessment(this.mergedFeedbacks, this.submissionId).subscribe(
            result => {
                this.jhiAlertService.success('modelingAssessmentEditor.messages.saveSuccessful');
            },
            error => this.jhiAlertService.error('modelingAssessmentEditor.messages.saveFailed'),
        );
    }

    onSubmit() {
        const escalatedConflicts: Conflict[] = this.getEscalatedConflicts();
        if (escalatedConflicts.length > 0) {
            const modalRef = this.modalService.open(ConflictEscalationModalComponent, { size: 'lg', backdrop: 'static' });
            modalRef.componentInstance.tutorsEscalatingTo = this.getDistinctTutorsEscalatingTo(escalatedConflicts);
            modalRef.componentInstance.escalatedConflictsCount = escalatedConflicts.length;
            modalRef.result.then(value => {
                this.modelingAssessmentService.escalateConflict(escalatedConflicts).subscribe(() => this.submitAssessment());
            });
        } else {
            this.submitAssessment();
        }
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

    setSameWidthOnModelingAssessments() {
        const conflictEditorWidth = $('#conflictEditor').width();
        const instructionsWidth = $('#assessmentInstructions').width();
        $('.resizable').css('width', (conflictEditorWidth - instructionsWidth) / 2 + 15);
    }

    private submitAssessment() {
        this.modelingAssessmentService.saveAssessment(this.mergedFeedbacks, this.submissionId, true).subscribe(
            result => {
                this.jhiAlertService.success('modelingAssessmentEditor.messages.submitSuccessful');
                this.router.navigate(['modeling-exercise', this.modelingExercise.id, 'submissions', this.submissionId, 'assessment']);
            },
            error => {
                if (error.status === 409) {
                    this.conflicts = error.error as Conflict[];
                    this.conflicts.forEach((conflict: Conflict) => {
                        this.modelingAssessmentService.convertResult(conflict.causingConflictingResult.result);
                        conflict.resultsInConflict.forEach((conflictingResult: ConflictingResult) => this.modelingAssessmentService.convertResult(conflictingResult.result));
                    });
                    this.initComponent();
                    this.jhiAlertService.clear();
                    this.jhiAlertService.error('modelingAssessmentEditor.messages.submitFailedWithConflict');
                } else {
                    this.jhiAlertService.clear();
                    this.jhiAlertService.error('modelingAssessmentEditor.messages.submitFailed');
                }
            },
        );
    }

    private updateSelectedConflict() {
        this.currentConflict = this.conflicts[this.conflictIndex];
        this.conflictingResult = this.currentConflict.resultsInConflict[0];
        this.conflictingModel = JSON.parse((this.conflictingResult.result.submission as ModelingSubmission).model);
        this.updateHighlightedElements();
        this.updateHighlightColor();
    }

    private updateHighlightedElements() {
        this.modelHighlightedElementIds = new Set<string>([this.currentConflict.causingConflictingResult.modelElementId]);
        this.conflictingModelHighlightedElementIds = new Set<string>([this.conflictingResult.modelElementId]);
    }

    private updateCurrentState() {
        const mergedFeedback = this.mergedFeedbacks.find((feedback: Feedback) => feedback.referenceId === this.currentConflict.causingConflictingResult.modelElementId);
        const conflictingFeedback = this.conflictingResult.result.feedbacks.find((feedback: Feedback) => feedback.referenceId === this.conflictingResult.modelElementId);
        if (mergedFeedback.credits !== conflictingFeedback.credits) {
            this.conflictResolutionStates[this.conflictIndex] = ConflictResolutionState.ESCALATED;
        } else {
            this.conflictResolutionStates[this.conflictIndex] = ConflictResolutionState.RESOLVED;
        }
        this.updateHighlightColor();
        this.updateOverallResolutionState();
    }

    private updateHighlightColor() {
        switch (this.conflictResolutionStates[this.conflictIndex]) {
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

import { AfterViewInit, Component, OnInit } from '@angular/core';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';
import { ActivatedRoute, Router } from '@angular/router';
import { UMLModel } from '@ls1intum/apollon';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { User } from 'app/core/user/user.model';
import * as $ from 'jquery';
import { JhiAlertService } from 'ng-jhipster';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { Feedback, FeedbackHighlightColor } from 'app/entities/feedback';
import { ConflictResolutionState } from 'app/modeling-assessment-editor/conflict-resolution-state.enum';
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';
import { StudentParticipation } from 'app/entities/participation';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-modeling-assessment-conflict',
    templateUrl: './modeling-assessment-conflict.component.html',
    styleUrls: ['./modeling-assessment-conflict.component.scss', '../modeling-assessment-editor.component.scss'],
})
export class ModelingAssessmentConflictComponent implements OnInit, AfterViewInit {
    model: UMLModel;
    mergedFeedbacks: Feedback[];
    currentFeedbacksCopy: Feedback[];
    modelHighlightedElements = new Map<string, string>(); // map elementId -> highlight color
    user: User | null;

    currentConflict: Conflict;
    conflictingResult: ConflictingResult;
    conflictingModel: UMLModel;
    conflictingModelHighlightedElements = new Map<string, string>(); // map elementId -> highlight color
    conflicts?: Conflict[];
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
            if (this.conflicts && this.conflicts.length > 0) {
                this.initComponent();
                const result = this.currentConflict.causingConflictingResult.result;
                this.model = JSON.parse((result.submission as ModelingSubmission).model);
                const studentParticipation = result.participation! as StudentParticipation;
                this.modelingExercise = studentParticipation.exercise as ModelingExercise;
            } else {
                this.conflicts = undefined;
                this.jhiAlertService.error('modelingAssessmentEditor.messages.noConflicts');
            }
        });
        this.accountService.identity().then(value => (this.user = value));
    }

    ngAfterViewInit() {
        this.setSameWidthOnModelingAssessments();
    }

    initComponent() {
        this.mergedFeedbacks = [...this.conflicts![0].causingConflictingResult.result.feedbacks];
        this.currentFeedbacksCopy = [...this.conflicts![0].causingConflictingResult.result.feedbacks];
        this.conflictResolutionStates = [...this.conflicts!.map(() => ConflictResolutionState.UNHANDLED)];
        this.updateSelectedConflict();
    }

    onNextConflict() {
        this.conflictIndex = this.conflictIndex < this.conflicts!.length - 1 ? ++this.conflictIndex : this.conflictIndex;
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
        this.currentFeedbacksCopy = [...this.mergedFeedbacks];
        this.updateCurrentState();
    }

    onAcceptOther() {
        this.updateFeedbackInMergedFeedback(
            this.currentConflict.causingConflictingResult.modelElementId,
            this.conflictingResult.modelElementId,
            this.conflictingResult.result.feedbacks,
        );
        this.currentFeedbacksCopy = [...this.mergedFeedbacks];
        this.updateCurrentState();
    }

    onFeedbackChanged(feedbacks: Feedback[]) {
        const elementAssessmentUpdate = feedbacks.find(feedback => feedback.referenceId === this.currentConflict.causingConflictingResult.modelElementId);
        const originalElementAssessment = this.currentConflict.causingConflictingResult.result.feedbacks.find(
            feedback => feedback.referenceId === this.currentConflict.causingConflictingResult.modelElementId,
        );
        if (originalElementAssessment && elementAssessmentUpdate && elementAssessmentUpdate.credits !== originalElementAssessment.credits) {
            this.updateCurrentState();
        }
        this.mergedFeedbacks = feedbacks;
    }

    onSave() {
        if (!this.modelingAssessmentService.isFeedbackTextValid(this.mergedFeedbacks)) {
            this.jhiAlertService.error('modelingAssessmentEditor.messages.feedbackTextTooLong');
            return;
        }

        this.modelingAssessmentService.saveAssessment(this.mergedFeedbacks, this.submissionId).subscribe(
            result => {
                this.jhiAlertService.success('modelingAssessmentEditor.messages.saveSuccessful');
            },
            error => this.jhiAlertService.error('modelingAssessmentEditor.messages.saveFailed'),
        );
    }

    onSubmit() {
        if (!this.modelingAssessmentService.isFeedbackTextValid(this.mergedFeedbacks)) {
            this.jhiAlertService.error('modelingAssessmentEditor.messages.feedbackTextTooLong');
            return;
        }

        const escalatedConflicts: Conflict[] = [];
        for (let i = 0; i < this.conflictResolutionStates.length; i++) {
            if (this.conflictResolutionStates[i] === ConflictResolutionState.ESCALATED) {
                escalatedConflicts.push(this.conflicts![i]);
            }
        }
        this.modelingAssessmentService.escalateConflict(escalatedConflicts).subscribe(value => {
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
        });
    }

    updateFeedbackInMergedFeedback(elementIdToUpdate: string, elementIdToUpdateWith: string, sourceFeedbacks: Feedback[]) {
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
        } else {
            console.error(`could not find expected feedback with id ${elementIdToUpdateWith} inside source feedback`);
        }
    }

    setSameWidthOnModelingAssessments() {
        const conflictEditorWidth = $('#conflictEditor').width();
        const instructionsWidth = $('#assessmentInstructions').width();
        $('.resizable').css('width', ((conflictEditorWidth || 0) - (instructionsWidth || 0)) / 2 + 15);
    }

    private updateSelectedConflict() {
        this.currentConflict = this.conflicts![this.conflictIndex];
        this.conflictingResult = this.currentConflict.resultsInConflict[0];
        this.conflictingModel = JSON.parse((this.conflictingResult.result.submission as ModelingSubmission).model);
        this.updateHighlightedElements();
        this.updateHighlightColor();
    }

    private updateHighlightedElements() {
        this.modelHighlightedElements = new Map<string, string>([[this.currentConflict.causingConflictingResult.modelElementId, FeedbackHighlightColor.RED]]);
        this.conflictingModelHighlightedElements = new Map<string, string>([[this.conflictingResult.modelElementId, FeedbackHighlightColor.RED]]);
    }

    private updateCurrentState() {
        const mergedFeedback = this.mergedFeedbacks.find((feedback: Feedback) => feedback.referenceId === this.currentConflict.causingConflictingResult.modelElementId)!;
        const conflictingFeedback = this.conflictingResult.result.feedbacks.find((feedback: Feedback) => feedback.referenceId === this.conflictingResult.modelElementId)!;
        if (mergedFeedback.credits !== conflictingFeedback.credits) {
            this.conflictResolutionStates[this.conflictIndex] = ConflictResolutionState.ESCALATED;
        } else {
            this.conflictResolutionStates[this.conflictIndex] = ConflictResolutionState.RESOLVED;
        }
        this.updateHighlightColor();
        this.updateOverallResolutioState();
    }

    private updateHighlightColor() {
        switch (this.conflictResolutionStates[this.conflictIndex]) {
            case ConflictResolutionState.UNHANDLED:
                this.setHighlightColorOfConflictElements(FeedbackHighlightColor.BLUE);
                break;
            case ConflictResolutionState.ESCALATED:
                this.setHighlightColorOfConflictElements(FeedbackHighlightColor.YELLOW);
                break;
            case ConflictResolutionState.RESOLVED:
                this.setHighlightColorOfConflictElements(FeedbackHighlightColor.GREEN);
                break;
        }
    }

    private setHighlightColorOfConflictElements(color: string) {
        const conflictingModelHighlightedElements = new Map<string, string>();
        for (const elementId of this.conflictingModelHighlightedElements.keys()) {
            conflictingModelHighlightedElements.set(elementId, color);
        }
        this.conflictingModelHighlightedElements = conflictingModelHighlightedElements;

        const modelHighlightedElements = new Map<string, string>();
        for (const elementId of this.modelHighlightedElements.keys()) {
            modelHighlightedElements.set(elementId, color);
        }
        this.modelHighlightedElements = modelHighlightedElements;
    }

    private updateOverallResolutioState() {
        const newConflictsAllHandled = this.conflictResolutionStates.every(state => state !== ConflictResolutionState.UNHANDLED);
        if (newConflictsAllHandled && !this.conflictsAllHandled) {
            this.jhiAlertService.success('modelingAssessmentEditor.messages.conflictsResolved');
        }
        this.conflictsAllHandled = newConflictsAllHandled;
    }
}

import { Component, OnInit } from '@angular/core';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { ConflictResolutionState } from 'app/modeling-assessment-editor/conflict-resolution-state.enum';
import { Feedback } from 'app/entities/feedback';
import { AccountService } from 'app/core';
import { UMLModel } from '@ls1intum/apollon';
import { ModelingAssessmentConflictService } from 'app/modeling-assessment-conflict/modeling-assessment-conflict.service';
import { JhiAlertService } from 'ng-jhipster';
import { ActivatedRoute, Router } from '@angular/router';
import { ModelingSubmission } from 'app/entities/modeling-submission';

@Component({
    selector: 'jhi-instructor-conflict-resolution',
    templateUrl: './instructor-conflict-resolution.component.html',
    styles: [],
})
export class InstructorConflictResolutionComponent implements OnInit {
    // private conflictId: number;
    conflicts: Conflict[] = [];
    modelingExercise: ModelingExercise;
    conflictResolutionStates: ConflictResolutionState[];

    currentState: ConflictResolutionState;
    currentConflict: Conflict | undefined;

    currentUserId: number | null;
    rightModel: UMLModel;
    rightConflictingResult: ConflictingResult | undefined;
    rightHighlightedElementIds: Set<string>;
    rightCenteredElementId: string;
    rightFeedbacksCopy: Feedback[];
    leftConflictingResult: ConflictingResult;

    leftModel: UMLModel;
    leftHighlightedElementIds: Set<string>;
    leftCenteredElementId: string;
    leftFeedbacksCopy: Feedback[];

    private chosenFeedback: Feedback | undefined;

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
            this.conflictService.getConflict(conflictId).subscribe(
                conflict => {
                    this.conflicts = [conflict];
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
        this.updateCurentState(ConflictResolutionState.UNHANDLED);
        this.onCurrentConflictChanged(0);
        if (this.currentConflict) {
            this.modelingExercise = this.currentConflict.causingConflictingResult.result.participation!.exercise as ModelingExercise;
        }
    }

    onCurrentConflictChanged(index: number) {
        this.currentConflict = this.conflicts[index];
        if (this.currentConflict) {
            this.rightConflictingResult = this.currentConflict.causingConflictingResult;
            this.rightFeedbacksCopy = JSON.parse(JSON.stringify(this.rightConflictingResult.result!.feedbacks));
            this.rightModel = JSON.parse((this.rightConflictingResult.result.submission as ModelingSubmission).model);
            this.leftConflictingResult = this.currentConflict.resultsInConflict[0];
            this.leftFeedbacksCopy = JSON.parse(JSON.stringify(this.leftConflictingResult.result!.feedbacks));
            this.leftModel = JSON.parse((this.leftConflictingResult.result.submission as ModelingSubmission).model);
            this.updateHighlightedElements();
            this.updateCenteredElements();
        }
    }

    onSubmit(escalatedConflicts: Conflict[]) {}

    useRight() {
        if (this.rightConflictingResult) {
            this.chosenFeedback = this.rightConflictingResult.getReferencedFeedback();
        }
        this.updateCurentState(ConflictResolutionState.RESOLVED);
    }

    useLeft() {
        if (this.leftConflictingResult) {
            this.chosenFeedback = this.leftConflictingResult.getReferencedFeedback();
        }
        this.updateCurentState(ConflictResolutionState.RESOLVED);
    }

    private findFeedbackById(feedbacks: Feedback[], referenceId: string): Feedback | undefined {
        return feedbacks.find(feedback => feedback.referenceId == referenceId);
    }

    // onFeedbackChanged(feedbacks: Feedback[]) {
    //     // this.mergedFeedbacks = feedbacks;
    // }

    // onConflictStateChanged(newState: ConflictResolutionState) {
    //     this.conflictResolutionStates[this.conflictIndex] = newState;
    //     this.currentState = newState;
    //     this.conflictResolutionStates = JSON.parse(JSON.stringify(this.conflictResolutionStates));
    // }

    // private initResolutionStates(conflicts: Conflict[]) {
    //     // TODO MJ move into service
    //     this.conflictResolutionStates = [];
    //     if (conflicts && conflicts.length > 0) {
    //         const mergedFeedbacks = conflicts[0].causingConflictingResult!.result.feedbacks;
    //         for (let i = 0; i < this.conflicts.length; i++) {
    //             const currentConflict: Conflict = conflicts[i];
    //             const conflictingResult: ConflictingResult = currentConflict.resultsInConflict[0];
    //             const mergedFeedback = mergedFeedbacks.find((feedback: Feedback) => feedback.referenceId === currentConflict.causingConflictingResult.modelElementId);
    //             const conflictingFeedback = conflictingResult.result.feedbacks.find((feedback: Feedback) => feedback.referenceId === conflictingResult.modelElementId);
    //             if (mergedFeedback && conflictingFeedback && mergedFeedback.credits !== conflictingFeedback.credits) {
    //                 this.conflictResolutionStates.push(ConflictResolutionState.UNHANDLED);
    //             } else {
    //                 this.conflictResolutionStates.push(ConflictResolutionState.RESOLVED);
    //             }
    //         }
    //     }
    //     this.currentState = this.conflictResolutionStates[this.conflictIndex];
    // }

    private updateCurentState(newState: ConflictResolutionState) {
        this.currentState = newState;
        this.conflictResolutionStates = [newState];
    }

    private updateHighlightedElements() {
        if (this.rightConflictingResult) {
            this.rightHighlightedElementIds = new Set<string>([this.rightConflictingResult.modelElementId]);
        }
        this.leftHighlightedElementIds = new Set<string>([this.leftConflictingResult.modelElementId]);
    }

    private updateCenteredElements() {
        this.rightCenteredElementId = this.rightHighlightedElementIds.values().next().value;
        this.leftCenteredElementId = this.leftHighlightedElementIds.values().next().value;
    }
}

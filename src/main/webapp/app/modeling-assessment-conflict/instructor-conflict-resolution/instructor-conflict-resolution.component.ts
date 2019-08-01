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
    tutorsChosenRightModel: number;

    leftConflictingResult: ConflictingResult;
    leftModel: UMLModel;
    leftHighlightedElementIds: Set<string>;
    leftCenteredElementId: string;
    leftFeedbacksCopy: Feedback[];
    tutorsChosenLeftModel: number;

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
            this.updateDicisionCounters();
        }
    }

    onSubmit(escalatedConflicts: Conflict[]) {
        if (this.currentConflict && this.chosenFeedback) {
            this.conflictService
                .resolveConflict(this.currentConflict, this.chosenFeedback)
                .subscribe(() => this.router.navigate(['course', this.modelingExercise.course!.id, 'exercise', this.modelingExercise.id, 'tutor-dashboard']));
        }
    }

    useRight() {
        if (this.rightConflictingResult) {
            this.chosenFeedback = ConflictingResult.getReferencedFeedback(this.rightConflictingResult);
        }
        this.updateCurentState(ConflictResolutionState.RESOLVED);
    }

    useLeft() {
        if (this.leftConflictingResult) {
            this.chosenFeedback = ConflictingResult.getReferencedFeedback(this.leftConflictingResult);
        }
        this.updateCurentState(ConflictResolutionState.RESOLVED);
    }

    private updateDicisionCounters() {
        if (this.currentConflict && this.rightConflictingResult) {
            const causingFeedbackCredit = this.findFeedbackById(this.rightConflictingResult.result.feedbacks, this.rightConflictingResult.modelElementId)!.credits;
            this.tutorsChosenRightModel = this.currentConflict.resultsInConflict.filter(conflRes => conflRes.updatedFeedback.credits === causingFeedbackCredit).length + 1;
            this.tutorsChosenLeftModel = this.currentConflict.resultsInConflict.length - this.tutorsChosenRightModel + 1;
        }
    }

    private findFeedbackById(feedbacks: Feedback[], referenceId: string): Feedback | undefined {
        return feedbacks.find(feedback => feedback.referenceId == referenceId);
    }

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

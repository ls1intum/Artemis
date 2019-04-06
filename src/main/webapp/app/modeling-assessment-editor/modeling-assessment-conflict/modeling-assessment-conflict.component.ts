import { AfterViewInit, Component, OnInit } from '@angular/core';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';
import { ActivatedRoute } from '@angular/router';
import { UMLModel } from '@ls1intum/apollon';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { AccountService, User } from 'app/core';
import * as $ from 'jquery';
import { ModelingAssessmentService } from 'app/modeling-assessment-editor';
import { JhiAlertService } from 'ng-jhipster';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { Feedback } from 'app/entities/feedback';

@Component({
    selector: 'jhi-modeling-assessment-conflict',
    templateUrl: './modeling-assessment-conflict.component.html',
    styleUrls: ['./modeling-assessment-conflict.component.scss'],
})
export class ModelingAssessmentConflictComponent implements OnInit, AfterViewInit {
    model: UMLModel;
    feedbacks: Feedback[];
    modelHighlightedElementIds: Set<string>;
    user: User;

    currentConflict: Conflict;
    conflictingResult: ConflictingResult;
    conflictingModel: UMLModel;
    conflictingModelHighlightedElementIds: Set<string>;
    conflicts: Conflict[];
    conflictIndex = 0;
    modelingExercise: ModelingExercise;

    constructor(
        private jhiAlertService: JhiAlertService,
        private route: ActivatedRoute,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private accountService: AccountService,
    ) {}

    ngOnInit() {
        this.route.params.subscribe(params => {
            const submissionId = Number(params['submissionId']);
            this.conflicts = this.modelingAssessmentService.getLocalConflicts(submissionId);
        });
        if (this.conflicts) {
            this.updateSelectedConflict();
            this.model = JSON.parse((this.currentConflict.result.submission as ModelingSubmission).model);
            this.modelingExercise = this.currentConflict.result.participation.exercise as ModelingExercise;
            this.feedbacks = this.currentConflict.result.feedbacks;
        } else {
            this.jhiAlertService.error('modelingAssessment.messages.noConflicts');
        }
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

    onKeepYours() {}

    onAcceptOther() {
        const otherFeedback: Feedback = this.conflictingResult.result.feedbacks.find((feedback: Feedback) => feedback.referenceId === this.conflictingResult.modelElementId);
        const ownFeedback: Feedback = this.feedbacks.find((feedback: Feedback) => feedback.referenceId === this.currentConflict.modelElementId);
        let feedbacks: Feedback[] = [];
        this.feedbacks.forEach(feedback => {
            if (feedback.referenceId === this.currentConflict.modelElementId) {
                feedback.credits = otherFeedback.credits;
            }
            feedbacks.push(feedback);
        });
        this.feedbacks = feedbacks;
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
    }

    private updateHighlightedElements() {
        this.modelHighlightedElementIds = new Set<string>([this.currentConflict.modelElementId]);
        this.conflictingModelHighlightedElementIds = new Set<string>([this.conflictingResult.modelElementId]);
    }

    private updateCenteredElement() {}
}

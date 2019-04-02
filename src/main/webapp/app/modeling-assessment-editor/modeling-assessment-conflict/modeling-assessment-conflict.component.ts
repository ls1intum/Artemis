import { Component, Input, OnInit } from '@angular/core';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { ActivatedRoute } from '@angular/router';
import { UMLModel } from '@ls1intum/apollon';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { AccountService, User } from 'app/core';
import * as $ from 'jquery';

@Component({
    selector: 'jhi-modeling-assessment-conflict',
    templateUrl: './modeling-assessment-conflict.component.html',
    styleUrls: ['./modeling-assessment-conflict.component.scss'],
})
export class ModelingAssessmentConflictComponent implements OnInit {
    model: UMLModel;
    conflictIndex = 0;
    user: User;

    currentConflict: Conflict;
    conflictingResult: ConflictingResult;
    conflictingModel: UMLModel;

    @Input() modelingExercise: ModelingExercise;
    @Input() conflicts: Conflict[] = [];

    constructor(private route: ActivatedRoute, private modelingSubmisionService: ModelingSubmissionService, private accountService: AccountService) {}

    ngOnInit() {
        if (this.conflicts) {
            this.updateSelectedConflict();
            this.model = JSON.parse((this.currentConflict.result.submission as ModelingSubmission).model);
        }
        this.accountService.identity().then(value => (this.user = value));
    }

    onNextConflict() {
        this.conflictIndex = this.conflictIndex < this.conflicts.length - 1 ? ++this.conflictIndex : this.conflictIndex;
        this.updateSelectedConflict();
    }

    onPrevConflict() {
        this.conflictIndex = this.conflictIndex > 0 ? --this.conflictIndex : this.conflictIndex;
        this.updateSelectedConflict();
    }

    // private loadSubmission() {
    //     this.route.params.subscribe(params => {
    //         const submissionId = Number(params['submissionId']);
    //         this.modelingSubmisionService.getSubmission(submissionId).subscribe((submission: ModelingSubmission) => {
    //             this.processNewSubmission(submission);
    //         });
    //     }); //TODO MJ error handling
    // }

    // private processNewSubmission(submission: ModelingSubmission) {
    //     this.submission = submission;
    //     this.modelingExercise = submission.participation.exercise as ModelingExercise;
    //     this.model = JSON.parse(submission.model);
    //     if (!this.model) {
    //         //TODO error message
    //     }
    // }

    private updateSelectedConflict() {
        this.currentConflict = this.conflicts[this.conflictIndex];
        this.conflictingResult = this.currentConflict.conflictingResults[0];
        this.conflictingModel = JSON.parse((this.conflictingResult.result.submission as ModelingSubmission).model);
    }
}

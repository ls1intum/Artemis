import { Component, Input, OnInit, Renderer2 } from '@angular/core';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { ActivatedRoute, UrlSegment } from '@angular/router';
import { UMLModel } from '@ls1intum/apollon';
import { Conflict } from 'app/modeling-assessment-editor/conflict.model';

@Component({
    selector: 'jhi-modeling-assessment-conflict',
    templateUrl: './modeling-assessment-conflict.component.html',
    styleUrls: ['./modeling-assessment-conflict.component.scss'],
})
export class ModelingAssessmentConflictComponent implements OnInit {
    model: UMLModel;
    conflictIndex: number = 0;

    @Input() submission: ModelingSubmission;
    @Input() modelingExercise: ModelingExercise;
    @Input() conflicts: Conflict[] = [];

    constructor(private route: ActivatedRoute, private modelingSubmisionService: ModelingSubmissionService) {}

    ngOnInit() {
        this.route.url.subscribe((urlSegments: UrlSegment[]) => {
            if (urlSegments.pop().path === 'conflict') {
                this.loadSubmision();
            }
        });
    }

    loadSubmision() {
        this.route.params.subscribe(params => {
            const submissionId = Number(params['submissionId']);
            this.modelingSubmisionService.getSubmission(submissionId).subscribe((submission: ModelingSubmission) => {
                this.submission = submission;
                this.modelingExercise = submission.participation.exercise as ModelingExercise;
                this.model = JSON.parse(submission.model);
            });
        });
    }
}

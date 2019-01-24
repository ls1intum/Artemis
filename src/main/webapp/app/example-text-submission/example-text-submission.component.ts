import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { Subscription } from 'rxjs';
import { Principal } from '../core';
import { ExampleSubmission } from 'app/entities/example-submission';
import { Exercise, ExerciseService } from 'app/entities/exercise';
import { HttpResponse } from '@angular/common/http';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { ExampleSubmissionService } from 'app/entities/example-submission/example-submission.service';

@Component({
    selector: 'jhi-example-text-submission',
    templateUrl: './example-text-submission.component.html',
    providers: [JhiAlertService]
})
export class ExampleTextSubmissionComponent implements OnInit {
    isNewSubmission: boolean;
    exerciseId: number;
    exampleSubmission = new ExampleSubmission();
    textSubmission = new TextSubmission();

    private exampleSubmissionId: number;
    private subscription: Subscription;
    private exercise: Exercise;

    constructor(
        private exerciseService: ExerciseService,
        private textSubmissionService: TextSubmissionService,
        private exampleSubmissionService: ExampleSubmissionService,
        private jhiAlertService: JhiAlertService,
        private principal: Principal,
        private route: ActivatedRoute
    ) {}

    ngOnInit(): void {
        // (+) converts string 'id' to a number
        this.subscription = this.route.params.subscribe(params => {
            this.exerciseId = +params.exerciseId;
            this.exampleSubmissionId = +params.exampleSubmissionId;
            this.isNewSubmission = this.exampleSubmissionId === -1;

            this.loadAll();
        });
    }

    loadAll() {
        this.exerciseService.find(this.exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            this.exercise = exerciseResponse.body;
        });

        if (!this.isNewSubmission) {
            this.exampleSubmissionService.get(this.exampleSubmissionId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                this.exampleSubmission = exampleSubmissionResponse.body;
                this.textSubmission = this.exampleSubmission.submission as TextSubmission;
            });

        }
    }

    upsertExampleTextSubmission() {
        if (this.isNewSubmission) {
            this.createNewExampleTextSubmission();
        } else {
            this.updateExampleTextSubmission();
        }
    }

    private createNewExampleTextSubmission() {
        const newSubmission = this.textSubmission;
        newSubmission.exampleSubmission = true;

        this.textSubmissionService.create(newSubmission, this.exerciseId).subscribe((submissionResponse: HttpResponse<TextSubmission>) => {
            this.textSubmission = submissionResponse.body;

            const newExampleSubmission = this.exampleSubmission;
            newExampleSubmission.submission = this.textSubmission;
            newExampleSubmission.exercise = this.exercise;

            this.exampleSubmissionService.create(newExampleSubmission, this.exerciseId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                this.exampleSubmission = exampleSubmissionResponse.body;
                this.exampleSubmissionId = this.exampleSubmission.id;
                this.isNewSubmission = false;

                this.jhiAlertService.success('arTeMiSApp.exampleSubmission.submitSuccessful');
            });
        });
    }

    private updateExampleTextSubmission() {
        this.textSubmission.exampleSubmission = true;

        let hasOneFinished = false;

        this.textSubmissionService.update(this.textSubmission, this.exerciseId).subscribe((submissionResponse: HttpResponse<TextSubmission>) => {
            this.textSubmission = submissionResponse.body;

            if (hasOneFinished) {
                this.jhiAlertService.success('arTeMiSApp.exampleSubmission.saveSuccessful');
            } else {
                hasOneFinished = true;
            }
        });
        this.exampleSubmissionService.update(this.exampleSubmission, this.exerciseId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body;

            if (hasOneFinished) {
                this.jhiAlertService.success('arTeMiSApp.exampleSubmission.saveSuccessful');
            } else {
                hasOneFinished = true;
            }
        });
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, null);
    }
}

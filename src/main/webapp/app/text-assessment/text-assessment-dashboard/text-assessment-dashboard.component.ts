import { Component, OnInit } from '@angular/core';
import { ExerciseService, ExerciseType } from 'app/entities/exercise';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { ActivatedRoute } from '@angular/router';
import { TextExercise } from 'app/entities/text-exercise';
import { DifferencePipe } from 'angular2-moment';
import { HttpResponse } from '@angular/common/http';

@Component({
    templateUrl: './text-assessment-dashboard.component.html',
    styleUrls: ['./text-assessment-dashboard.component.css']
})
export class TextAssessmentDashboardComponent implements OnInit {
    exercise: TextExercise;
    submissions: TextSubmission[] = [];
    busy = false;
    predicate = 'id';
    reverse = false;

    constructor(
        private route: ActivatedRoute,
        private exerciseService: ExerciseService,
        private textSubmissionService: TextSubmissionService,
        private momentDiff: DifferencePipe
    ) {}

    async ngOnInit() {
        this.busy = true;
        const exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.exerciseService
            .find(exerciseId)
            .map(exerciseResponse => {
                if (exerciseResponse.body.type !== ExerciseType.TEXT) {
                    throw new Error('Cannot use Text Assessment Dashboard with non-text Exercise type.');
                }

                return <TextExercise>exerciseResponse.body;
            })
            .subscribe(exercise => {
                this.exercise = exercise;
                this.getSubmissions();
            });
    }

    private getSubmissions(): void {
        this.textSubmissionService
            .getTextSubmissionsForExercise(this.exercise, { submittedOnly: true })
            .map((response: HttpResponse<TextSubmission[]>) =>
                response.body.map((submission: TextSubmission) => {
                    if (submission.result) {
                        // reconnect some associations
                        submission.result.submission = submission;
                        submission.result.participation = submission.participation;
                        submission.participation.results = [submission.result];
                    }

                    return submission;
                })
            )
            .subscribe((submissions: TextSubmission[]) => {
                this.submissions = submissions;
                this.busy = false;
            });
    }

    durationString(completionDate: Date, initializationDate: Date) {
        return this.momentDiff.transform(completionDate, initializationDate, 'minutes');
    }
}

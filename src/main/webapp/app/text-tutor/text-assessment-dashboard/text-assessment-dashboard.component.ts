import { Component, OnInit } from '@angular/core';
import { ExerciseService, ExerciseType } from 'app/entities/exercise';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { ActivatedRoute } from '@angular/router';
import { TextExercise } from 'app/entities/text-exercise';
import { DifferencePipe } from 'angular2-moment';

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
        private momentDiff: DifferencePipe,
    ) { }

    async ngOnInit() {
        this.busy = true;
        const exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const exerciseResponse = await this.exerciseService.find(exerciseId).toPromise();
        if (exerciseResponse.body.type !== ExerciseType.TEXT) {
            throw new Error('Cannot use Text Assessment Dashboard with non-text Exercise type.');
        }
        this.exercise = <TextExercise>exerciseResponse.body;

        await this.getSubmissions();
        this.busy = false;
    }

    private async getSubmissions(): Promise<void> {
        const response = await this.textSubmissionService.getTextSubmissionsForExercise(this.exercise, { submittedOnly: true }).toPromise();
        this.submissions = response.body.map(submission => {
            if (submission.result) {
                // reconnect some associations
                submission.result.submission = submission;
                submission.result.participation = submission.participation;
                submission.participation.results = [submission.result];
            }

            return submission;
        });
    }

    durationString(completionDate: Date, initializationDate: Date) {
        return this.momentDiff.transform(completionDate, initializationDate, 'minutes');
    }

}

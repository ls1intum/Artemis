import { Component, OnInit } from '@angular/core';
import { ExerciseService, ExerciseType } from 'app/entities/exercise';
import { ActivatedRoute } from '@angular/router';
import { DifferencePipe } from 'ngx-moment';
import { HttpResponse } from '@angular/common/http';
import { FileUploadExercise } from 'app/entities/file-upload-exercise';
import { FileUploadSubmission, FileUploadSubmissionService } from 'app/entities/file-upload-submission';

@Component({
    templateUrl: './file-upload-assessment-dashboard.component.html',
    styles: [],
})
export class FileUploadAssessmentDashboardComponent implements OnInit {
    exercise: FileUploadExercise;
    submissions: FileUploadSubmission[] = [];
    busy = false;
    predicate = 'id';
    reverse = false;

    constructor(
        private route: ActivatedRoute,
        private exerciseService: ExerciseService,
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private momentDiff: DifferencePipe,
    ) {}

    async ngOnInit() {
        this.busy = true;
        const exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.exerciseService
            .find(exerciseId)
            .map(exerciseResponse => {
                if (exerciseResponse.body!.type !== ExerciseType.FILE_UPLOAD) {
                    throw new Error('Cannot use File Upload Assessment Dashboard with different Exercise type.');
                }

                return <FileUploadExercise>exerciseResponse.body!;
            })
            .subscribe(exercise => {
                this.exercise = exercise;
                this.getSubmissions();
            });
    }

    private getSubmissions(): void {
        this.fileUploadSubmissionService
            .getFileUploadSubmissionsForExercise(this.exercise.id, { submittedOnly: true })
            .map((response: HttpResponse<FileUploadSubmission[]>) =>
                response.body!.map((submission: FileUploadSubmission) => {
                    if (submission.result) {
                        // reconnect some associations
                        submission.result.submission = submission;
                        submission.result.participation = submission.participation;
                        submission.participation.results = [submission.result];
                    }

                    return submission;
                }),
            )
            .subscribe((submissions: FileUploadSubmission[]) => {
                this.submissions = submissions;
                this.busy = false;
            });
    }

    public durationString(completionDate: Date, initializationDate: Date) {
        return this.momentDiff.transform(completionDate, initializationDate, 'minutes');
    }
}

import { Component, OnInit } from '@angular/core';
import { TextExercise } from 'app/entities/text-exercise';
import { TextSubmission } from 'app/entities/text-submission';
import { HighlightColors } from '../text-shared/highlight-colors';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router } from '@angular/router';
import { Result, ResultService } from 'app/entities/result';
import { TextAssessmentsService } from 'app/entities/text-assessments/text-assessments.service';
import { Feedback } from 'app/entities/feedback';
import { Participation } from 'app/entities/participation';

@Component({
    providers: [TextAssessmentsService],
    templateUrl: './text-assessment.component.html',
    styles: []
})
export class TextAssessmentComponent implements OnInit {
    text: string;
    participation: Participation;
    submission: TextSubmission;
    result: Result;
    assessments: Feedback[] = [];
    exercise: TextExercise;
    totalScore = 0;
    assessmentsAreValid: boolean;
    invalidError: string;
    isAuthorized = true;
    accountId = 0;
    busy = true;

    public getColorForIndex = HighlightColors.forIndex;

    constructor(
        private jhiAlertService: JhiAlertService,
        private modalService: NgbModal,
        private router: Router,
        private route: ActivatedRoute,
        private resultService: ResultService,
        private assessmentsService: TextAssessmentsService
    ) {
        this.assessments = [];
        this.assessmentsAreValid = false;
    }

    public ngOnInit(): void {
        this.busy = true;
        const exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const submissionId = Number(this.route.snapshot.paramMap.get('submissionId'));

        this.assessmentsService.getFeedbackDataForExerciseSubmission(exerciseId, submissionId).subscribe(participation => {
            this.participation = participation;
            this.submission = <TextSubmission>this.participation.submissions[0];
            this.exercise = <TextExercise>this.participation.exercise;
            this.result = this.participation.results[0];
            this.assessments = this.result.feedbacks;
            if (!this.assessments) {
                this.assessments = [];
            }
            this.busy = false;
        });
    }

    public addAssessment(assessmentText: string): void {
        const assessment = new Feedback();
        assessment.reference = assessmentText;
        assessment.credits = 0;
        this.assessments.push(assessment);
        this.checkScoreBoundaries();
    }

    public deleteAssessment(assessmentToDelete: Feedback): void {
        this.assessments = this.assessments.filter(elem => elem !== assessmentToDelete);
        this.checkScoreBoundaries();
    }

    public save(): void {
        this.checkScoreBoundaries();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('arTeMiSApp.textAssessment.invalidAssessments');
            return;
        }

        this.assessmentsService.save(this.assessments, this.exercise.id, this.result.id).subscribe(response => {
            this.result = response.body;
            this.jhiAlertService.success('arTeMiSApp.textAssessment.saveSuccessful');
        });
    }

    public submit(): void {
        this.checkScoreBoundaries();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('arTeMiSApp.textAssessment.invalidAssessments');
            return;
        }

        this.assessmentsService.submit(this.assessments, this.exercise.id, this.result.id).subscribe(response => {
            response.body.participation.results = [response.body];
            this.result = response.body;
            this.jhiAlertService.success('arTeMiSApp.textAssessment.submitSuccessful');
        });
    }

    public previous(): void {
        this.router.navigate(['text', this.exercise.id, 'assessment']);
    }

    /**
     * Calculates the total score of the current assessment.
     * Returns an error if the total score cannot be calculated
     * because a score is not a number/empty.
     */
    public checkScoreBoundaries() {
        if (!this.assessments || this.assessments.length === 0) {
            this.totalScore = 0;
            this.assessmentsAreValid = true;
            return;
        }

        const credits = this.assessments.map(assessment => assessment.credits);

        if (!credits.every(credit => credit !== null)) {
            this.invalidError = 'The score field must be a number and can not be empty!';
            this.assessmentsAreValid = false;
            return;
        }

        this.totalScore = credits.reduce((a, b) => a + b, 0);
        this.assessmentsAreValid = true;
        this.invalidError = null;
    }
}

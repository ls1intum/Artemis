import { Component, OnInit } from '@angular/core';
import { TextExercise } from 'app/entities/text-exercise';
import { TextSubmission } from 'app/entities/text-submission';
import { TextAssessment } from 'app/entities/text-assessments/text-assessments.model';
import { colorForIndex } from 'app/text/tutor/highlight-colors';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router } from '@angular/router';
import { Result, ResultService } from 'app/entities/result';
import { Principal } from 'app/core';
import { TextAssessmentsService } from 'app/entities/text-assessments/text-assessments.service';

@Component({
    providers: [TextAssessmentsService],
    templateUrl: './tutor.component.html',
    styles: []
})
export class ArTEMiSTextTutorComponent implements OnInit {
    text: string;
    submission: TextSubmission;
    result: Result;
    assessments: TextAssessment[] = [];
    exercise: TextExercise;
    totalScore = 0;
    assessmentsAreValid: boolean;
    invalidError: string;
    isAuthorized = true;
    accountId = 0;
    done = false;
    busy = false;

    public getColorForIndex = colorForIndex;

    constructor(
        private jhiAlertService: JhiAlertService,
        private modalService: NgbModal,
        private router: Router,
        private route: ActivatedRoute,
        private resultService: ResultService,
        private assessmentsService: TextAssessmentsService,
        private principal: Principal
    ) {
        this.assessments = [];
        this.assessmentsAreValid = false;
        this.done = true;
    }

    public async ngOnInit() {
        const exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const submissionId = Number(this.route.snapshot.paramMap.get('submissionId'));

        const data = await this.assessmentsService.getFeedbackDataForExerciseSubmission(exerciseId, submissionId).toPromise();
        this.submission = data.submission;
        this.exercise = data.exercise;
        this.assessments = data.assessments;
        this.result = data.result;
    }

    public addAssessment(assessmentText: string): void {
        const assessment = new TextAssessment(assessmentText, 0, null);
        this.assessments.push(assessment);
        this.checkScoreBoundaries();
    }

    public deleteAssessment(assessmentToDelete: TextAssessment): void {
        this.assessments = this.assessments.filter(elem => elem !== assessmentToDelete);
        this.checkScoreBoundaries();
    }

    public async save(): Promise<void> {
        this.checkScoreBoundaries();
        const response = await this.assessmentsService.save(this.assessments, this.exercise.id, this.result.id).toPromise();
        this.result = response.body;
        this.jhiAlertService.success('arTeMiSApp.textAssessment.saveSuccessful');
    }

    public async submit(): Promise<void> {
        this.checkScoreBoundaries();
    }

    public previous(): void {
        console.log('previousState');
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

    public assessNextOptimal(): void {
        console.log('assessNextOptimal()');
    }
}

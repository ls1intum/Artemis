import { ChangeDetectorRef, Component, OnDestroy, OnInit, AfterViewInit } from '@angular/core';
import { Location } from '@angular/common';
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
import * as interact from 'interactjs';
import { Interactable } from 'interactjs';
import { WindowRef } from 'app/core';

@Component({
    providers: [TextAssessmentsService, WindowRef],
    templateUrl: './text-assessment.component.html',
    styleUrls: ['./text-assessment-component.scss'],
})
export class TextAssessmentComponent implements OnInit, OnDestroy, AfterViewInit {
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
    showResult = true;

    /** Resizable constants **/
    resizableMinWidth = 100;
    resizableMaxWidth = 1200;
    interactResizable: Interactable;

    public getColorForIndex = HighlightColors.forIndex;

    constructor(
        private changeDetectorRef: ChangeDetectorRef,
        private jhiAlertService: JhiAlertService,
        private modalService: NgbModal,
        private router: Router,
        private route: ActivatedRoute,
        private resultService: ResultService,
        private assessmentsService: TextAssessmentsService,
        private location: Location,
        private $window: WindowRef,
    ) {
        this.assessments = [];
        this.assessmentsAreValid = false;
    }

    public ngOnInit(): void {
        this.busy = true;
        const exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const submissionValue = this.route.snapshot.paramMap.get('submissionId');

        if (submissionValue === 'new') {
            this.assessmentsService.getParticipationForSubmissionWithoutAssessment(exerciseId).subscribe(participation => {
                this.participation = participation;
                this.result = new Result();
                this.submission = <TextSubmission>this.participation.submissions[0];
                this.exercise = <TextExercise>this.participation.exercise;
                this.assessments = [];
                this.busy = false;

                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${this.participation.id}`);
                this.location.go(newUrl);
            });
        } else {
            const submissionId = Number(submissionValue);

            this.assessmentsService.getFeedbackDataForExerciseSubmission(exerciseId, submissionId).subscribe(participation => {
                this.participation = participation;
                this.submission = <TextSubmission>this.participation.submissions[0];
                this.exercise = <TextExercise>this.participation.exercise;
                this.result = this.participation.results[0];
                this.assessments = this.result.feedbacks || [];
                this.busy = false;
                this.checkScoreBoundaries();
            });
        }
    }

    /**
     * @function ngAfterViewInit
     * @desc After the view was initialized, we create an interact.js resizable object,
     *       designate the edges which can be used to resize the target element and set min and max values.
     *       The 'resizemove' callback function processes the event values and sets new width and height values for the element.
     */
    ngAfterViewInit(): void {
        this.resizableMinWidth = this.$window.nativeWindow.screen.width / 6;
        this.interactResizable = interact('.resizable-submission')
            .resizable({
                // Enable resize from right edge; triggered by class .resizing-bar
                edges: { left: false, right: '.resizing-bar', bottom: false, top: false },
                // Set min and max width
                restrictSize: {
                    min: { width: this.resizableMinWidth },
                    max: { width: this.resizableMaxWidth },
                },
                inertia: true,
            })
            .on('resizemove', function(event) {
                const target = event.target;
                // Update element width
                target.style.width = event.rect.width + 'px';
            });
    }

    public ngOnDestroy(): void {
        this.changeDetectorRef.detach();
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
            this.updateParticipationWithResult();
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
            this.result = response.body;
            this.updateParticipationWithResult();
            this.jhiAlertService.success('arTeMiSApp.textAssessment.submitSuccessful');
        });
    }

    private updateParticipationWithResult(): void {
        this.showResult = false;
        this.changeDetectorRef.detectChanges();
        this.participation.results[0] = this.result;
        this.showResult = true;
        this.changeDetectorRef.detectChanges();
    }

    public previous(): void {
        this.location.back();
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

        if (!credits.every(credit => credit !== null && !isNaN(credit))) {
            this.invalidError = 'The score field must be a number and can not be empty!';
            this.assessmentsAreValid = false;
            return;
        }

        this.totalScore = credits.reduce((a, b) => a + b, 0);
        this.assessmentsAreValid = true;
        this.invalidError = null;
    }
}

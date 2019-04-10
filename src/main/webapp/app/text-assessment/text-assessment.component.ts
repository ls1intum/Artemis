import * as $ from 'jquery';

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
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

@Component({
    providers: [TextAssessmentsService, WindowRef],
    templateUrl: './text-assessment.component.html',
    styleUrls: ['./text-assessment.component.scss'],
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

    formattedProblemStatement: string;
    formattedSampleSolution: string;
    formattedGradingInstructions: string;

    /** Resizable constants **/
    resizableMinWidth = 100;
    resizableMaxWidth = 1200;
    resizableMinHeight = 200;
    interactResizable: Interactable;
    interactResizableTop: Interactable;

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
        private artemisMarkdown: ArtemisMarkdown,
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
                this.receiveParticipation(participation);

                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${this.submission.id}`);
                this.location.go(newUrl);
            });
        } else {
            const submissionId = Number(submissionValue);
            this.assessmentsService.getFeedbackDataForExerciseSubmission(exerciseId, submissionId).subscribe(participation => this.receiveParticipation(participation));
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
        this.resizableMinHeight = this.$window.nativeWindow.screen.height / 7;

        this.interactResizable = interact('.resizable-submission')
            .resizable({
                // Enable resize from left edge; triggered by class .resizing-bar
                edges: { left: '.resizing-bar', right: false, bottom: false, top: false },
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

        this.interactResizableTop = interact('.resizable-horizontal')
            .resizable({
                // Enable resize from bottom edge; triggered by class .resizing-bar-bottom
                edges: { left: false, right: false, top: false, bottom: '.resizing-bar-bottom' },
                // Set min height
                restrictSize: {
                    min: { height: this.resizableMinHeight },
                },
                inertia: true,
            })
            .on('resizemove', function(event) {
                const target = event.target;
                // Update element height
                target.style.minHeight = event.rect.height + 'px';
                $('#submission-area').css('min-height', event.rect.height - 100 + 'px');
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
        if (!this.result.id) {
            return; // We need to have saved the result before
        }

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

    public predefineTextBlocks(): void {
        this.assessmentsService.getResultWithPredefinedTextblocks(this.result.id).subscribe(response => {
            this.assessments = response.body.feedbacks || [];
        });
    }

    private updateParticipationWithResult(): void {
        this.showResult = false;
        this.changeDetectorRef.detectChanges();
        this.participation.results[0] = this.result;
        this.showResult = true;
        this.changeDetectorRef.detectChanges();
    }

    private receiveParticipation(participation: Participation): void {
        this.participation = participation;
        this.submission = <TextSubmission>this.participation.submissions[0];
        this.exercise = <TextExercise>this.participation.exercise;

        this.formattedGradingInstructions = this.artemisMarkdown.htmlForMarkdown(this.exercise.gradingInstructions);
        this.formattedProblemStatement = this.artemisMarkdown.htmlForMarkdown(this.exercise.problemStatement);
        this.formattedSampleSolution = this.artemisMarkdown.htmlForMarkdown(this.exercise.sampleSolution);

        this.result = this.participation.results[0];
        this.assessments = this.result.feedbacks || [];
        this.busy = false;
        this.checkScoreBoundaries();
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

    toggleCollapse($event: any) {
        const target = $event.toElement || $event.relatedTarget || $event.target;
        target.blur();
        const $card = $(target).closest('#instructions');

        if ($card.hasClass('collapsed')) {
            $card.removeClass('collapsed');
            this.interactResizable.resizable({ enabled: true });
            $card.css({ width: this.resizableMinWidth + 'px' });
        } else {
            $card.addClass('collapsed');
            $card.css({ width: '55px' });
            this.interactResizable.resizable({ enabled: false });
        }
    }

    public get headingTranslationKey(): string {
        const baseKey = 'arTeMiSApp.textAssessment.heading.';

        if (this.submission && this.submission.exampleSubmission) {
            return baseKey + 'exampleAssessment';
        }
        return baseKey + 'assessment';
    }
}

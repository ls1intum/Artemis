import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { TextExercise, TextExerciseService } from 'app/entities/text-exercise';
import { Result } from 'app/entities/result';
import { Participation, ParticipationService } from 'app/entities/participation';
import { TextEditorService } from 'app/text-editor/text-editor.service';
import * as moment from 'moment';
import { HighlightColors } from 'app/text-shared/highlight-colors';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { Feedback } from 'app/entities/feedback';
import { Language } from 'app/entities/tutor-group';
import * as franc from 'franc';

@Component({
    templateUrl: './text-editor.component.html',
    providers: [ParticipationService],
})
export class TextEditorComponent implements OnInit {
    textExercise: TextExercise;
    participation: Participation;
    result: Result;
    submission: TextSubmission;
    isActive: boolean;
    isSaving: boolean;
    answer: string;
    isExampleSubmission = false;
    showComplaintForm = false;
    // indicates if there is a complaint for the result of the submission
    hasComplaint: boolean;
    // the number of complaints that the student is still allowed to submit in the course. this is used for disabling the complain button.
    numberOfAllowedComplaints: number;
    // indicates if the result is older than one week. if it is, the complain button is disabled
    resultOlderThanOneWeek: boolean;
    // indicates if the assessment due date is in the past. the assessment will not be loaded and displayed to the student if it is not.
    isAfterAssessmentDueDate: boolean;

    public getColorForIndex = HighlightColors.forIndex;
    private submissionConfirmationText: string;

    constructor(
        private route: ActivatedRoute,
        private textExerciseService: TextExerciseService,
        private participationService: ParticipationService,
        private textSubmissionService: TextSubmissionService,
        private textService: TextEditorService,
        private complaintService: ComplaintService,
        private jhiAlertService: JhiAlertService,
        private artemisMarkdown: ArtemisMarkdown,
        private location: Location,
        translateService: TranslateService,
    ) {
        this.isSaving = false;
        translateService.get('arTeMiSApp.textExercise.confirmSubmission').subscribe(text => (this.submissionConfirmationText = text));
    }

    ngOnInit() {
        const participationId = Number(this.route.snapshot.paramMap.get('participationId'));
        if (Number.isNaN(participationId)) {
            return this.jhiAlertService.error('arTeMiSApp.textExercise.error', null, null);
        }

        this.textService.get(participationId).subscribe(
            (data: Participation) => {
                this.participation = data;
                this.textExercise = this.participation.exercise as TextExercise;
                this.isAfterAssessmentDueDate = !this.textExercise.assessmentDueDate || moment().isAfter(this.textExercise.assessmentDueDate);

                if (this.textExercise.course) {
                    this.complaintService.getNumberOfAllowedComplaintsInCourse(this.textExercise.course.id).subscribe((allowedComplaints: number) => {
                        this.numberOfAllowedComplaints = allowedComplaints;
                    });
                }

                if (data.submissions && data.submissions.length > 0) {
                    this.submission = data.submissions[0] as TextSubmission;
                    if (this.submission && data.results && this.isAfterAssessmentDueDate) {
                        this.result = data.results.find(r => r.submission.id === this.submission.id);
                    }

                    if (this.submission && this.submission.text) {
                        this.answer = this.submission.text;
                    }
                    if (this.result && this.result.completionDate) {
                        this.resultOlderThanOneWeek = moment(this.result.completionDate).isBefore(moment().subtract(1, 'week'));
                        this.complaintService.findByResultId(this.result.id).subscribe(res => {
                            this.hasComplaint = !!res.body;
                        });
                    }
                }

                this.isActive = this.textExercise.dueDate === undefined || this.textExercise.dueDate === null || new Date() <= moment(this.textExercise.dueDate).toDate();
            },
            (error: HttpErrorResponse) => this.onError(error),
        );
    }

    get generalFeedback(): Feedback | null {
        if (this.result && this.result.feedbacks && Array.isArray(this.result.feedbacks)) {
            return this.result.feedbacks.find(f => f.reference == null) || null;
        }

        return null;
    }

    saveText() {
        if (this.isSaving) {
            return;
        }

        if (!this.submission) {
            this.submission = new TextSubmission();
        }

        this.submission.submitted = false;
        this.submission.text = this.answer;
        this.isSaving = true;

        this.textSubmissionService[this.submission.id ? 'update' : 'create'](this.submission, this.textExercise.id).subscribe(
            response => {
                if (response) {
                    this.submission = response.body;
                    this.result = this.submission.result;
                    this.jhiAlertService.success('arTeMiSApp.textExercise.saveSuccessful');

                    this.isSaving = false;
                }
            },
            e => {
                this.jhiAlertService.error('arTeMiSApp.textExercise.error');
                this.isSaving = false;
            },
        );
    }

    submit() {
        if (!this.submission) {
            return;
        }

        this.submission.text = this.answer;
        const languageProbabilities = franc.all(this.answer);

        for (const languageProbability of languageProbabilities) {
            if (languageProbability[0] === 'und') {
                // Language is undetermined
                break;
            }
            if (languageProbability[0] === 'eng') {
                // Language is english
                this.submission.language = Language.ENGLISH;
                break;
            }
            if (languageProbability[0] === 'deu' || languageProbability[0] === 'nds') {
                // Language is german or lower german
                this.submission.language = Language.GERMAN;
                break;
            }
        }

        const confirmSubmit = window.confirm(this.submissionConfirmationText);

        if (confirmSubmit) {
            this.submission.submitted = true;
            this.textSubmissionService.update(this.submission, this.textExercise.id).subscribe(
                response => {
                    this.submission = response.body;
                    this.result = this.submission.result;

                    if (this.isActive) {
                        this.jhiAlertService.success('arTeMiSApp.textExercise.submitSuccessful');
                    } else {
                        this.jhiAlertService.warning('arTeMiSApp.textExercise.submitDeadlineMissed');
                    }
                },
                err => {
                    this.jhiAlertService.error('arTeMiSApp.modelingEditor.error');
                    this.submission.submitted = false;
                },
            );
        }
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, null);
    }

    previous() {
        this.location.back();
    }
}

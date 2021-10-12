import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { AlertService } from 'app/core/util/alert.service';
import { HttpResponse } from '@angular/common/http';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { TutorParticipationService } from 'app/exercises/shared/dashboards/tutor/tutor-participation.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AccountService } from 'app/core/auth/account.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { tutorAssessmentTour } from 'app/guided-tour/tours/tutor-assessment-tour';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { Feedback, FeedbackCorrectionError } from 'app/entities/feedback.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Result } from 'app/entities/result.model';
import { setLatestSubmissionResult } from 'app/entities/submission.model';
import { TextAssessmentBaseComponent } from 'app/exercises/text/assess/text-assessment-base.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { notUndefined } from 'app/shared/util/global.utils';
import { AssessButtonStates, Context, State, SubmissionButtonStates, UIStates } from 'app/exercises/text/manage/example-text-submission/example-text-submission-state.model';
import { filter } from 'rxjs/operators';
import { FeedbackMarker, ExampleSubmissionAssessCommand } from 'app/exercises/shared/example-submission/example-submission-assess-command';

@Component({
    selector: 'jhi-example-text-submission',
    templateUrl: './example-text-submission.component.html',
    styleUrls: ['./example-text-submission.component.scss'],
})
export class ExampleTextSubmissionComponent extends TextAssessmentBaseComponent implements OnInit, Context, FeedbackMarker {
    isNewSubmission: boolean;
    areNewAssessments = true;
    unsavedChanges = false;
    private exerciseId: number;
    private exampleSubmissionId: number;
    exampleSubmission = new ExampleSubmission();
    assessmentsAreValid = false;
    result?: Result;
    private unreferencedFeedback: Feedback[] = [];
    totalScore: number;
    readOnly: boolean;
    toComplete: boolean;
    state = State.initialWithContext(this);
    SubmissionButtonStates = SubmissionButtonStates;
    AssessButtonStates = AssessButtonStates;
    UIStates = UIStates;

    constructor(
        alertService: AlertService,
        accountService: AccountService,
        assessmentsService: TextAssessmentService,
        structuredGradingCriterionService: StructuredGradingCriterionService,
        private exerciseService: ExerciseService,
        private textSubmissionService: TextSubmissionService,
        private exampleSubmissionService: ExampleSubmissionService,
        private tutorParticipationService: TutorParticipationService,
        private route: ActivatedRoute,
        private router: Router,
        private location: Location,
        private artemisMarkdown: ArtemisMarkdownService,
        private resultService: ResultService,
        private guidedTourService: GuidedTourService,
    ) {
        super(alertService, accountService, assessmentsService, structuredGradingCriterionService);
        this.textBlockRefs = [];
        this.unusedTextBlockRefs = [];
        this.submission = new TextSubmission();
    }

    private get referencedFeedback(): Feedback[] {
        return this.textBlockRefs.map(({ feedback }) => feedback).filter(notUndefined) as Feedback[];
    }

    private get assessments(): Feedback[] {
        return [...this.referencedFeedback, ...this.unreferencedFeedback];
    }

    /**
     * Reads route params and loads the example submission on initialWithContext.
     */
    async ngOnInit(): Promise<void> {
        await super.ngOnInit();
        // (+) converts string 'id' to a number
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const exampleSubmissionId = this.route.snapshot.paramMap.get('exampleSubmissionId');
        this.readOnly = !!this.route.snapshot.queryParamMap.get('readOnly');
        this.toComplete = !!this.route.snapshot.queryParamMap.get('toComplete');

        if (exampleSubmissionId === 'new') {
            this.isNewSubmission = true;
            this.exampleSubmissionId = -1;
        } else {
            this.exampleSubmissionId = +exampleSubmissionId!;
        }
        this.loadAll();
    }

    /**
     * Loads the exercise.
     * Also loads the example submission if the new parameter is not set.
     */
    private loadAll(): void {
        this.exerciseService.find(this.exerciseId).subscribe((exerciseResponse: HttpResponse<TextExercise>) => {
            this.exercise = exerciseResponse.body!;
            this.guidedTourService.enableTourForExercise(this.exercise, tutorAssessmentTour, false);
        });

        if (this.isNewSubmission) {
            return; // We don't need to load anything else
        }
        this.state.edit();

        this.exampleSubmissionService.get(this.exampleSubmissionId).subscribe(async (exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body!;
            this.submission = this.exampleSubmission.submission as TextSubmission;
            await this.fetchExampleResult();
            if (this.toComplete) {
                this.state = State.forCompletion(this);
                this.textBlockRefs.forEach((ref) => delete ref.feedback);
                this.validateFeedback();
            } else if (this.result?.id) {
                this.state = State.forExistingAssessmentWithContext(this);
            }
            // do this here to make sure  everythign is loaded before the guided tour step is loaded
            this.guidedTourService.componentPageLoaded();
        });
    }

    private fetchExampleResult(): Promise<void> {
        return new Promise((resolve) => {
            this.assessmentsService
                .getExampleResult(this.exerciseId, this.submission?.id!)
                .pipe(filter(notUndefined))
                .subscribe((result) => {
                    this.result = result;
                    this.exampleSubmission.submission = this.submission = result.submission;
                    this.prepareTextBlocksAndFeedbacks();
                    this.areNewAssessments = this.assessments.length <= 0;
                    this.validateFeedback();
                    resolve();
                });
        });
    }

    /**
     * Creates the example submission.
     */
    createNewExampleTextSubmission(): void {
        const newExampleSubmission = new ExampleSubmission();
        newExampleSubmission.submission = this.submission!;
        newExampleSubmission.exercise = this.exercise;

        this.exampleSubmissionService.create(newExampleSubmission, this.exerciseId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body!;
            this.exampleSubmission.exercise = this.exercise;
            this.exampleSubmissionId = this.exampleSubmission.id!;
            this.submission = this.exampleSubmission.submission as TextSubmission;
            this.isNewSubmission = false;

            // Update the url with the new id, without reloading the page, to make the history consistent
            const newUrl = window.location.hash.replace('#', '').replace('new', `${this.exampleSubmissionId}`);
            this.location.go(newUrl);

            this.resultService.createNewExampleResult(this.exerciseId!, this.submission.id!).subscribe((response: HttpResponse<Result>) => {
                this.result = response.body!;
                this.state.edit();
                this.alertService.success('artemisApp.exampleSubmission.submitSuccessful');
            }, this.alertService.error);
        }, this.alertService.error);
    }

    /**
     * Updates the example submission.
     */
    updateExampleTextSubmission(): void {
        this.exampleSubmissionService.update(this.exampleSubmissionForNetwork(), this.exerciseId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body!;
            this.state.edit();
            this.unsavedChanges = false;
            this.alertService.success('artemisApp.exampleSubmission.saveSuccessful');
        }, this.alertService.error);
    }

    public async startAssessment(): Promise<void> {
        await this.fetchExampleResult();
        this.state.assess();
    }
    /**
     * Checks if the score boundaries have been respected and save the assessment.
     */
    public saveAssessments(): void {
        this.validateFeedback();
        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }
        this.assessmentsService.saveExampleAssessment(this.exerciseId, this.exampleSubmission.id!, this.assessments, this.textBlocksWithFeedback).subscribe((response) => {
            this.result = response.body!;
            this.areNewAssessments = false;
            this.alertService.success('artemisApp.textAssessment.saveSuccessful');
            this.state.assess();
            if (this.unsavedChanges) {
                this.exampleSubmissionService
                    .update(this.exampleSubmissionForNetwork(), this.exerciseId)
                    .subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                        this.exampleSubmission = exampleSubmissionResponse.body!;
                        this.unsavedChanges = false;
                    }, this.alertService.error);
            }
        });
    }

    /**
     * Redirects back to the assessment dashboard if route param readOnly or toComplete is set.
     * Otherwise redirects back to the exercise's edit view either for exam exercises or normal exercises.
     */
    async back(): Promise<void> {
        const courseId = this.course?.id;
        // check if exam exercise
        if (!!this.exercise?.exerciseGroup) {
            const examId = this.exercise.exerciseGroup.exam?.id;
            const exerciseGroupId = this.exercise.exerciseGroup.id;
            if (this.readOnly || this.toComplete) {
                await this.router.navigate(['/course-management', courseId, 'assessment-dashboard', this.exerciseId]);
            } else {
                await this.router.navigate([
                    '/course-management',
                    courseId,
                    'exams',
                    examId,
                    'exercise-groups',
                    exerciseGroupId,
                    'text-exercises',
                    this.exerciseId,
                    'example-submissions',
                ]);
            }
        } else {
            if (this.readOnly || this.toComplete) {
                this.router.navigate(['/course-management', courseId, 'assessment-dashboard', this.exerciseId]);
            } else {
                this.router.navigate(['/course-management', courseId, 'text-exercises', this.exerciseId, 'example-submissions']);
            }
        }
    }

    /**
     * Checks the assessment of the tutor to the example submission tutorial.
     * The tutor is informed if the given points of the assessment are fine, too low or too high.
     */
    checkAssessment(): void {
        this.validateFeedback();
        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        const command = new ExampleSubmissionAssessCommand(this.tutorParticipationService, this.alertService, this);
        command.assessExampleSubmission(this.exampleSubmissionForNetwork(), this.exerciseId);
    }

    markAllFeedbackToCorrect() {
        this.textBlockRefs
            .map((ref) => ref.feedback)
            .filter((feedback) => feedback != undefined)
            .forEach((feedback) => {
                feedback!.correctionStatus = 'CORRECT';
            });
    }

    markWrongFeedback(correctionErrors: FeedbackCorrectionError[]) {
        correctionErrors.forEach((correctionError) => {
            const textBlockRef = this.textBlockRefs.find((ref) => ref.feedback?.reference === correctionError.reference);
            if (textBlockRef != undefined && textBlockRef.feedback != undefined) {
                textBlockRef.feedback.correctionStatus = correctionError.type;
            }
        });
    }

    private exampleSubmissionForNetwork() {
        const exampleSubmission = Object.assign({}, this.exampleSubmission);
        exampleSubmission.submission = Object.assign({}, this.submission);
        const result = Object.assign({}, this.result);
        setLatestSubmissionResult(exampleSubmission.submission, result);
        result.feedbacks = this.assessments;
        delete result?.submission;
        return exampleSubmission;
    }

    /**
     * Validate the feedback of the assessment
     */
    validateFeedback(): void {
        this.assessmentsAreValid = this.referencedFeedback.filter(Feedback.isPresent).length > 0;
        this.totalScore = this.computeTotalScore(this.assessments);

        if (this.guidedTourService.currentTour && this.toComplete) {
            this.guidedTourService.updateAssessmentResult(this.assessments.length, this.totalScore);
        }
    }

    /**
     * After the tutor declared that he read and understood the example submission a corresponding submission will be added to the
     * tutor participation of the exercise. Then a success alert is invoked and the user gets redirected back.
     */
    readAndUnderstood(): void {
        this.tutorParticipationService.assessExampleSubmission(this.exampleSubmission, this.exerciseId).subscribe(() => {
            this.alertService.success('artemisApp.exampleSubmission.readSuccessfully');
            this.back();
        });
    }

    private prepareTextBlocksAndFeedbacks() {
        const matchBlocksWithFeedbacks = TextAssessmentService.matchBlocksWithFeedbacks(this.submission?.blocks || [], this.result?.feedbacks || []);
        this.sortAndSetTextBlockRefs(matchBlocksWithFeedbacks, this.textBlockRefs, this.unusedTextBlockRefs, this.submission);
    }

    editSubmission(): void {
        this.assessmentsService.deleteExampleFeedback(this.exercise!.id!, this.exampleSubmission?.id!).subscribe();
        delete this.submission?.blocks;
        delete this.result?.feedbacks;
        this.textBlockRefs = [];
        this.unusedTextBlockRefs = [];
        this.state.edit();
    }
}

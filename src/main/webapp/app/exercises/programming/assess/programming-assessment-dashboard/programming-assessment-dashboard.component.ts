import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';
import { Result } from 'app/entities/result.model';
import { getLatestSubmissionResult, setLatestSubmissionResult, Submission } from 'app/entities/submission.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { SortService } from 'app/shared/service/sort.service';
import { AccountService } from 'app/core/auth/account.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ProgrammingAssessmentManualResultService } from '../manual-result/programming-assessment-manual-result.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { areManualResultsAllowed } from 'app/exercises/shared/exercise/exercise-utils';

@Component({
    templateUrl: './programming-assessment-dashboard.component.html',
})
export class ProgrammingAssessmentDashboardComponent implements OnInit {
    ExerciseType = ExerciseType;
    exercise: ProgrammingExercise;
    submissions: ProgrammingSubmission[] = [];
    filteredSubmissions: ProgrammingSubmission[] = [];
    busy = false;
    predicate = 'id';
    reverse = false;
    numberOfCorrectionrounds = 1;
    newManualResultAllowed: boolean;
    automaticType = AssessmentType.AUTOMATIC;
    private cancelConfirmationText: string;

    constructor(
        private route: ActivatedRoute,
        private exerciseService: ExerciseService,
        private accountService: AccountService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private programmingAssessmentManualResultService: ProgrammingAssessmentManualResultService,
        private translateService: TranslateService,
        private sortService: SortService,
    ) {
        translateService.get('artemisApp.programmingAssessment.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    /**
     * Life cycle hook to indicate component creation is done
     */
    async ngOnInit() {
        this.busy = true;
        const exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.exerciseService
            .find(exerciseId)
            .map((exerciseResponse) => {
                if (exerciseResponse.body!.type !== ExerciseType.PROGRAMMING) {
                    throw new Error('Cannot use Programming Assessment Dashboard with non-programming Exercise type.');
                }
                return <ProgrammingExercise>exerciseResponse.body!;
            })
            .subscribe((exercise) => {
                this.exercise = exercise;
                this.getSubmissions();
                this.numberOfCorrectionrounds = this.exercise.exerciseGroup ? this.exercise!.exerciseGroup.exam!.numberOfCorrectionRoundsInExam! : 1;
                this.setPermissions();
                this.newManualResultAllowed = areManualResultsAllowed(this.exercise);
                console.log(this.newManualResultAllowed);
            });
    }

    public sortRows() {
        this.sortService.sortByProperty(this.submissions, this.predicate, this.reverse);
    }

    private getSubmissions(): void {
        this.programmingSubmissionService
            .getProgrammingSubmissionsForExerciseByCorrectionRound(this.exercise.id!, { submittedOnly: true })
            .map((response: HttpResponse<ProgrammingSubmission[]>) =>
                response.body!.map((submission: ProgrammingSubmission) => {
                    const tmpResult = getLatestSubmissionResult(submission);
                    setLatestSubmissionResult(submission, tmpResult);
                    if (tmpResult) {
                        // reconnect some associations
                        tmpResult!.submission = submission;
                        tmpResult!.participation = submission.participation;
                        submission.participation!.results = [tmpResult!];
                    }

                    return submission;
                }),
            )
            .subscribe((submissions: ProgrammingSubmission[]) => {
                this.submissions = submissions;
                this.filteredSubmissions = submissions;
                this.filteredSubmissions.forEach((sub) => {
                    sub.results = sub.results!.filter((r) => r.assessmentType !== AssessmentType.AUTOMATIC);
                });
                this.busy = false;
            });
    }

    /**
     * Update the submission filter for assessments
     * @param {Submission[]} filteredSubmissions - Submissions to be filtered for
     */
    updateFilteredSubmissions(filteredSubmissions: Submission[]) {
        this.filteredSubmissions = filteredSubmissions as ProgrammingSubmission[];
    }

    /**
     * Get the assessment type of a result
     * @param {Result} result - Result to get the assessment type for
     */
    public assessmentTypeTranslationKey(result?: Result): string {
        if (result) {
            return `artemisApp.AssessmentType.${result.assessmentType}`;
        }
        return 'artemisApp.AssessmentType.null';
    }
    private setPermissions() {
        if (this.exercise.course) {
            this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.course!);
        } else {
            this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.exerciseGroup?.exam?.course!);
        }
    }

    /**
     * Cancel the current assessment and reload the submissions to reflect the change.
     */
    cancelAssessment(submission: Submission) {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.programmingAssessmentManualResultService.cancelAssessment(submission.id!).subscribe(() => {
                this.getSubmissions();
            });
        }
    }
    /**
     * get the link for the assessment of a specific submission of the current exercise
     * @param submissionId
     */
    getAssessmentLink(submission: Submission) {
        const participationId = submission.participation?.id;
        if (this.exercise.exerciseGroup) {
            return ['/course-management', this.exercise.exerciseGroup.exam?.course?.id, 'programming-exercises', this.exercise.id, 'code-editor', participationId, 'assessment'];
        } else {
            return ['/course-management', this.exercise.course?.id, 'programming-exercises', this.exercise.id, 'code-editor', participationId, 'assessment'];
        }
    }
}

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
import { areManualResultsAllowed } from 'app/exercises/shared/exercise/exercise.utils';
import { getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { map } from 'rxjs/operators';
import { faBan, faEdit, faFolderOpen, faSort } from '@fortawesome/free-solid-svg-icons';
import { AbstractAssessmentDashboard } from 'app/exercises/shared/dashboards/tutor/abstract-assessment-dashboard';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import dayjs from 'dayjs/esm';

@Component({
    templateUrl: './programming-exercise-submissions.component.html',
})
export class ProgrammingExerciseSubmissionsComponent extends AbstractAssessmentDashboard implements OnInit {
    readonly ExerciseType = ExerciseType;
    readonly AssessmentType = AssessmentType;
    exercise: ProgrammingExercise;
    submissions: ProgrammingSubmission[] = [];
    filteredSubmissions: ProgrammingSubmission[] = [];
    busy = false;
    predicate = 'id';
    reverse = false;
    courseId: number;
    exerciseId: number;
    examId: number;
    exerciseGroupId: number;
    numberOfCorrectionrounds = 1;
    newManualResultAllowed: boolean;
    automaticType = AssessmentType.AUTOMATIC;
    private cancelConfirmationText: string;
    public practiceMode = false;

    // Icons
    faSort = faSort;
    faBan = faBan;
    faEdit = faEdit;
    faFolderOpen = faFolderOpen;

    constructor(
        private route: ActivatedRoute,
        private exerciseService: ExerciseService,
        private accountService: AccountService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private programmingAssessmentManualResultService: ProgrammingAssessmentManualResultService,
        private translateService: TranslateService,
        private sortService: SortService,
    ) {
        super();
        translateService.get('artemisApp.programmingAssessment.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    /**
     * Life cycle hook to indicate component creation is done
     */
    async ngOnInit() {
        this.busy = true;
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        if (this.route.snapshot.paramMap.has('examId')) {
            this.examId = Number(this.route.snapshot.paramMap.get('examId'));
            this.exerciseGroupId = Number(this.route.snapshot.paramMap.get('exerciseGroupId'));
        }

        this.route.queryParams.subscribe((queryParams) => {
            if (queryParams['filterOption']) {
                this.filterOption = Number(queryParams['filterOption']);
            }
        });

        this.exerciseService
            .find(this.exerciseId)
            .pipe(
                map((exerciseResponse) => {
                    if (exerciseResponse.body!.type !== ExerciseType.PROGRAMMING) {
                        throw new Error('Cannot use Programming Assessment Dashboard with non-programming Exercise type.');
                    }
                    return <ProgrammingExercise>exerciseResponse.body!;
                }),
            )
            .subscribe((exercise) => {
                this.exercise = exercise;
                this.getSubmissions();
                this.numberOfCorrectionrounds = this.exercise.exerciseGroup ? this.exercise!.exerciseGroup.exam!.numberOfCorrectionRoundsInExam! : 1;
                this.newManualResultAllowed = areManualResultsAllowed(this.exercise);
            });
    }

    public isPracticeModeAvailable(): boolean {
        switch (this.exercise.type) {
            case ExerciseType.QUIZ:
                const quizExercise: QuizExercise = this.exercise as QuizExercise;
                return quizExercise.isOpenForPractice! && quizExercise.quizEnded!;
            case ExerciseType.PROGRAMMING:
                const programmingExercise: ProgrammingExercise = this.exercise as ProgrammingExercise;
                return dayjs().isAfter(dayjs(programmingExercise.dueDate));
            default:
                return false;
        }
    }

    public isInPracticeMode(): boolean {
        return this.practiceMode;
    }

    public togglePracticeMode(toggle: boolean): void {
        if (this.isPracticeModeAvailable()) {
            this.practiceMode = toggle;
            this.ngOnInit();
        }
    }

    public sortRows() {
        this.sortService.sortByProperty(this.submissions, this.predicate, this.reverse);
    }

    private getSubmissions(): void {
        this.programmingSubmissionService
            .getProgrammingSubmissionsForExerciseByCorrectionRound(this.exercise.id!, { submittedOnly: true })
            .pipe(
                map((response: HttpResponse<ProgrammingSubmission[]>) =>
                    response.body!.map((submission: ProgrammingSubmission) => {
                        const tmpResult = getLatestSubmissionResult(submission);
                        setLatestSubmissionResult(submission, tmpResult);
                        if (tmpResult) {
                            // reconnect some associations
                            tmpResult.participation = submission.participation;
                            submission.participation!.results = [tmpResult];
                        }

                        return submission;
                    }),
                ),
            )
            .subscribe((submissions: ProgrammingSubmission[]) => {
                this.submissions = submissions;
                switch (this.exercise.type) {
                    case ExerciseType.QUIZ:
                    case ExerciseType.PROGRAMMING:
                        this.filteredSubmissions = submissions.filter((submission) => submission.participation!['testRun'] === this.practiceMode);
                        break;
                    default:
                        this.filteredSubmissions = submissions;
                        break;
                }
                this.filteredSubmissions = submissions.filter((submission) => submission.participation!['testRun'] === this.practiceMode);
                this.filteredSubmissions.forEach((sub) => {
                    if (sub.results?.length) {
                        sub.results = sub.results.filter((r) => r.assessmentType !== AssessmentType.AUTOMATIC);
                    }
                });
                this.applyChartFilter(this.filteredSubmissions);
                this.busy = false;
            });
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
     */
    getAssessmentLink(participationId: number, submissionId: number) {
        return getLinkToSubmissionAssessment(this.exercise.type!, this.courseId, this.exerciseId, participationId, submissionId, this.examId, this.exerciseGroupId);
    }
}

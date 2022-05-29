import { Component, Input, OnChanges } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType, getIcon, IncludedInOverallScore } from 'app/entities/exercise.model';
import { Exam } from 'app/entities/exam.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { faArrowLeft, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { SubmissionType } from 'app/entities/submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';

@Component({
    selector: 'jhi-header-exercise-page-with-details',
    templateUrl: './header-exercise-page-with-details.component.html',
})
export class HeaderExercisePageWithDetailsComponent implements OnChanges {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    @Input() public exercise: Exercise;
    @Input() public studentParticipation?: StudentParticipation;
    @Input() public onBackClick: () => void; // TODO: This can be removed once we are happy with the breadcrumb navigation
    @Input() public title: string;
    @Input() public exam?: Exam;
    @Input() public isTestRun = false;
    @Input() public displayBackButton = true; // TODO: This can be removed once we are happy with the breadcrumb navigation
    @Input() public submissionPolicy?: SubmissionPolicy;

    public exerciseStatusBadge = 'bg-success';
    public exerciseCategories: ExerciseCategory[];
    public isExamMode = false;
    public dueDate?: dayjs.Dayjs;
    public numberOfSubmissions: number;

    icon: IconProp;

    // Icons
    faArrowLeft = faArrowLeft;
    faQuestionCircle = faQuestionCircle;

    /**
     * Sets the status badge and categories of the exercise on changes
     */
    ngOnChanges(): void {
        this.exerciseCategories = this.exercise.categories || [];

        if (this.exercise) {
            this.setIcon(this.exercise.type);
        }

        if (this.exam) {
            this.isExamMode = true;
        }

        if (this.exercise && !this.isExamMode) {
            this.dueDate = getExerciseDueDate(this.exercise, this.studentParticipation);
        }

        this.setExerciseStatusBadge();

        if (this.submissionPolicy) {
            let submissionCompensation = 0;
            if (this.studentParticipation?.submissions && this.studentParticipation?.submissions.length > 0) {
                submissionCompensation = (this.studentParticipation?.submissions.first()?.results?.length || 0) === 0 ? 1 : 0;
            }

            const commitHashSet = new Set<string>();
            this.studentParticipation?.submissions
                ?.filter((submission) => submission.type === SubmissionType.MANUAL && (!submission.results?.length || 0) === 0)
                .map((submission) => (submission as ProgrammingSubmission).commitHash)
                .forEach((commitHash: string) => commitHashSet.add(commitHash));

            this.numberOfSubmissions = submissionCompensation + commitHashSet.size;
        }
    }

    private setExerciseStatusBadge(): void {
        if (this.exercise) {
            if (this.exam) {
                this.exerciseStatusBadge = dayjs().isAfter(dayjs(this.exam.endDate!)) ? 'bg-danger' : 'bg-success';
            } else {
                this.exerciseStatusBadge = hasExerciseDueDatePassed(this.exercise, this.studentParticipation) ? 'bg-danger' : 'bg-success';
            }
        }
    }

    private setIcon(exerciseType?: ExerciseType) {
        if (exerciseType) {
            this.icon = getIcon(exerciseType) as IconProp;
        }
    }
}

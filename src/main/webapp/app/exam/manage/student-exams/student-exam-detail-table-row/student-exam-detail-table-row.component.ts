import { Component, OnChanges, input } from '@angular/core';
import { Exercise, ExerciseType, IncludedInOverallScore, getIcon } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { Course } from 'app/entities/course.model';
import { Result } from 'app/entities/result.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { faFolderOpen } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AssessmentType } from 'app/entities/assessment-type.model';

@Component({
    selector: '[jhi-student-exam-detail-table-row]',
    templateUrl: './student-exam-detail-table-row.component.html',
    providers: [],
    imports: [FaIconComponent, TranslateDirective, RouterLink, ArtemisTranslatePipe],
})
export class StudentExamDetailTableRowComponent implements OnChanges {
    exercise = input.required<Exercise>();
    examId = input.required<number>();
    isTestRun = input.required<boolean>();
    course = input.required<Course>();
    busy = input.required<boolean>();
    studentExam = input.required<StudentExam>();
    achievedPointsPerExercise = input.required<{
        [exerciseId: number]: number;
    }>();

    courseId: number;
    studentParticipation: StudentParticipation;
    submission: Submission;
    result: Result;
    openingAssessmentEditorForNewSubmission = false;
    readonly ExerciseType = ExerciseType;
    readonly AssessmentType = AssessmentType;
    getIcon = getIcon;

    // Icons
    faFolderOpen = faFolderOpen;

    ngOnChanges() {
        if (this.exercise().studentParticipations?.[0]) {
            this.studentParticipation = this.exercise().studentParticipations![0];
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            if (this.studentParticipation.submissions?.length! > 0) {
                this.submission = this.studentParticipation.submissions![0];
            }
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            if (this.studentParticipation.results?.length! > 0) {
                this.result = this.studentParticipation.results![0];
            }
        }
        if (this.course() && this.course().id) {
            this.courseId = this.course().id!;
        }
    }

    /**
     * get the link for the assessment of a specific submission of the current exercise
     * @param exercise
     * @param submission
     * @param resultId
     */
    getAssessmentLink(exercise: Exercise, submission?: Submission, resultId?: number) {
        let route;
        if (!exercise || !exercise.type) {
            return;
        }

        if (submission) {
            this.openingAssessmentEditorForNewSubmission = true;
            route = getLinkToSubmissionAssessment(
                exercise.type,
                this.courseId,
                exercise.id!,
                this.studentParticipation?.id,
                submission.id!,
                this.examId(),
                exercise.exerciseGroup?.id,
                resultId,
            );
            this.openingAssessmentEditorForNewSubmission = false;
        }
        return route;
    }

    /**
     * Gets the bonus points from the given exercise according to its includedInOverallScore value.
     * @param exercise exercise with or without bonus points
     */
    getBonusPoints(exercise?: Exercise): number | undefined {
        if (!exercise) {
            return 0;
        }
        switch (exercise.includedInOverallScore) {
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                return exercise.bonusPoints;
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
                return exercise.maxPoints;
            default:
                return 0;
        }
    }

    /**
     * Gets the max points from the given exercise according to its includedInOverallScore value.
     * @param exercise relevant exercise
     */
    getMaxPoints(exercise?: Exercise): number | undefined {
        if (!exercise) {
            return 0;
        }
        switch (exercise.includedInOverallScore) {
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                return exercise.maxPoints;
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
            default:
                return 0;
        }
    }
}

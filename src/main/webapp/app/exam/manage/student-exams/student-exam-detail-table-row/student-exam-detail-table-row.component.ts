import { Component, Input, OnChanges } from '@angular/core';
import { Exercise, ExerciseType, getIcon } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { getExerciseSubmissionsLink, getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Course } from 'app/entities/course.model';
import { Result } from 'app/entities/result.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { faFolderOpen } from '@fortawesome/free-solid-svg-icons';

@Component({
    /* tslint:disable-next-line component-selector */
    selector: '[jhi-student-exam-detail-table-row]',
    templateUrl: './student-exam-detail-table-row.component.html',
    providers: [],
})
export class StudentExamDetailTableRowComponent implements OnChanges {
    @Input() exercise: Exercise;
    @Input() examId: number;
    @Input() isTestRun: boolean;
    @Input() course: Course;
    @Input() busy: boolean;
    @Input() studentExam: StudentExam;

    courseId: number;
    studentParticipation: StudentParticipation;
    submission: Submission;
    result: Result;
    openingAssessmentEditorForNewSubmission = false;
    readonly ExerciseType = ExerciseType;
    getIcon = getIcon;

    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;

    // Icons
    faFolderOpen = faFolderOpen;

    ngOnChanges() {
        if (this.exercise.studentParticipations?.[0]) {
            this.studentParticipation = this.exercise.studentParticipations![0];
            if (this.studentParticipation.submissions?.length! > 0) {
                this.submission = this.studentParticipation.submissions![0];
            }
            if (this.studentParticipation.results?.length! > 0) {
                this.result = this.studentParticipation.results![0];
            }
        }
        if (this.course && this.course.id) {
            this.courseId = this.course.id!;
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

        if (exercise.type === ExerciseType.PROGRAMMING) {
            route = getExerciseSubmissionsLink(exercise.type, this.courseId, exercise.id!, this.examId, exercise.exerciseGroup?.id!);
        } else if (submission) {
            this.openingAssessmentEditorForNewSubmission = true;
            route = getLinkToSubmissionAssessment(exercise.type, this.courseId, exercise.id!, undefined, submission.id!, this.examId, exercise.exerciseGroup?.id!, resultId);
            this.openingAssessmentEditorForNewSubmission = false;
        }
        return route;
    }
}

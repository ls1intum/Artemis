import { Component, Input, OnInit } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exercise, ExerciseType, getIcon, IncludedInOverallScore } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { ActivatedRoute } from '@angular/router';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { Exam } from 'app/entities/exam.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { SubmissionType } from 'app/entities/submission.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { faAngleDown, faAngleRight, faFolderOpen, faInfoCircle, faPrint } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-exam-participation-summary',
    templateUrl: './exam-participation-summary.component.html',
    styleUrls: ['../../../course/manage/course-exercise-card.component.scss', '../../../exercises/quiz/shared/quiz.scss'],
})
export class ExamParticipationSummaryComponent implements OnInit {
    // make constants available to html for comparison
    readonly TEXT = ExerciseType.TEXT;
    readonly QUIZ = ExerciseType.QUIZ;
    readonly MODELING = ExerciseType.MODELING;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;
    readonly AssessmentType = AssessmentType;
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly SUBMISSION_TYPE_ILLEGAL = SubmissionType.ILLEGAL;

    @Input()
    studentExam: StudentExam;

    @Input()
    instructorView = false;

    collapsedExerciseIds: number[] = [];

    courseId: number;

    isTestRun = false;

    testRunConduction = false;

    examWithOnlyIdAndStudentReviewPeriod: Exam;

    // Icons
    faFolderOpen = faFolderOpen;
    faInfoCircle = faInfoCircle;
    faPrint = faPrint;
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;

    constructor(private route: ActivatedRoute, private serverDateService: ArtemisServerDateService, private courseManagementService: CourseManagementService) {}

    /**
     * Initialise the courseId from the current url
     */
    ngOnInit(): void {
        // flags required to display test runs correctly
        this.isTestRun = this.route.snapshot.url[1]?.toString() === 'test-runs';
        this.testRunConduction = this.isTestRun && this.route.snapshot.url[3]?.toString() === 'conduction';
        // courseId is not part of the exam or the exercise
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.setExamWithOnlyIdAndStudentReviewPeriod();
        let course;
        this.courseManagementService.find(this.courseId).subscribe((courseResponse) => (course = courseResponse.body!));
        for (const exercise of this.studentExam.exercises!) {
            exercise.studentParticipations!.first()!.exercise = exercise;
            exercise.exerciseGroup!.exam = this.examWithOnlyIdAndStudentReviewPeriod;
        }
        this.examWithOnlyIdAndStudentReviewPeriod.course = course;
    }

    getIcon(exerciseType: ExerciseType) {
        return getIcon(exerciseType);
    }

    asProgrammingExercise(exercise: Exercise): ProgrammingExercise {
        return exercise as ProgrammingExercise;
    }

    get resultsPublished() {
        if (this.testRunConduction) {
            return false;
        } else if (this.isTestRun) {
            return true;
        }
        return this.studentExam?.exam?.publishResultsDate && dayjs(this.studentExam.exam.publishResultsDate).isBefore(dayjs());
    }

    /**
     * called for exportPDF Button
     */
    printPDF() {
        // expand all exercises before printing
        this.collapsedExerciseIds = [];
        setTimeout(() => window.print());
    }

    public generateLink(exercise: Exercise) {
        if (exercise && exercise.studentParticipations && exercise.studentParticipations.length > 0) {
            return ['/courses', this.courseId, `${exercise.type}-exercises`, exercise.id, 'participate', exercise.studentParticipations[0].id];
        }
    }

    /**
     * @param exercise
     * returns the students submission for the exercise, undefined if no participation could be found
     */
    getSubmissionForExercise(exercise: Exercise) {
        if (
            exercise &&
            exercise.studentParticipations &&
            exercise.studentParticipations.length > 0 &&
            exercise.studentParticipations[0].submissions &&
            exercise.studentParticipations[0].submissions.length > 0
        ) {
            return exercise.studentParticipations[0].submissions[0];
        }
    }

    /**
     * @param exercise
     * returns the students submission for the exercise, undefined if no participation could be found
     */
    getParticipationForExercise(exercise: Exercise) {
        if (exercise.studentParticipations && exercise.studentParticipations[0]) {
            return exercise.studentParticipations[0];
        }
    }

    /**
     * @param exerciseId
     * checks collapse control of exercise cards depending on exerciseId
     */
    isCollapsed(exerciseId: number): boolean {
        return this.collapsedExerciseIds.includes(exerciseId);
    }

    /**
     * @param exerciseId
     * adds collapse control of exercise cards depending on exerciseId
     * @param exerciseId the exercise for which the submission should be collapsed
     */
    toggleCollapseExercise(exerciseId: number): void {
        const collapsed = this.isCollapsed(exerciseId);
        if (collapsed) {
            this.collapsedExerciseIds = this.collapsedExerciseIds.filter((id) => id !== exerciseId);
        } else {
            this.collapsedExerciseIds.push(exerciseId);
        }
    }

    /**
     * We only need to pass these values to the ComplaintInteractionComponent
     */
    setExamWithOnlyIdAndStudentReviewPeriod() {
        const exam = new Exam();
        exam.id = this.studentExam?.exam?.id;
        exam.examStudentReviewStart = this.studentExam?.exam?.examStudentReviewStart;
        exam.examStudentReviewEnd = this.studentExam?.exam?.examStudentReviewEnd;
        this.examWithOnlyIdAndStudentReviewPeriod = exam;
    }

    /**
     * Used to decide whether to instantiate the ComplaintInteractionComponent. We always instantiate the component if
     * the review dates are set and the review start date has passed.
     */
    isAfterStudentReviewStart() {
        if (this.isTestRun) {
            return true;
        }
        if (this.studentExam?.exam?.examStudentReviewStart && this.studentExam.exam.examStudentReviewEnd) {
            return this.serverDateService.now().isAfter(this.studentExam.exam.examStudentReviewStart);
        }
        return false;
    }

    isBeforeStudentReviewEnd() {
        if (this.isTestRun) {
            return true;
        }
        if (this.studentExam?.exam?.examStudentReviewStart && this.studentExam.exam.examStudentReviewEnd) {
            return this.serverDateService.now().isBefore(this.studentExam.exam.examStudentReviewEnd);
        }
        return false;
    }
}

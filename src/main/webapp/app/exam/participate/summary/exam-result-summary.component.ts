import { Component, Input, OnInit } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exercise, ExerciseType, IncludedInOverallScore, getIcon } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { ActivatedRoute } from '@angular/router';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { Exam } from 'app/entities/exam.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { SubmissionType } from 'app/entities/submission.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { faAngleDown, faAngleRight, faFolderOpen, faInfoCircle, faPrint } from '@fortawesome/free-solid-svg-icons';
import { ThemeService } from 'app/core/theme/theme.service';
import { StudentExamWithGradeDTO } from 'app/exam/exam-scores/exam-score-dtos.model';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { PlagiarismCaseInfo } from 'app/exercises/shared/plagiarism/types/PlagiarismCaseInfo';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

type ExerciseInfo = {
    icon: IconProp;
    isCollapsed: boolean;
    achievedPercentage?: number;
    colorClass?: string;
};

@Component({
    selector: 'jhi-exam-participation-summary',
    templateUrl: './exam-result-summary.component.html',
    styleUrls: ['../../../course/manage/course-exercise-card.component.scss', '../../../exercises/quiz/shared/quiz.scss', 'exam-result-summary.component.scss'],
})
export class ExamResultSummaryComponent implements OnInit {
    // make constants available to html for comparison
    readonly TEXT = ExerciseType.TEXT;
    readonly QUIZ = ExerciseType.QUIZ;
    readonly MODELING = ExerciseType.MODELING;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;
    readonly AssessmentType = AssessmentType;
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly SUBMISSION_TYPE_ILLEGAL = SubmissionType.ILLEGAL;
    readonly PlagiarismVerdict = PlagiarismVerdict;

    /**
     * Current student's exam.
     */
    private _studentExam: StudentExam;
    plagiarismCaseInfos: { [exerciseId: number]: PlagiarismCaseInfo } = {};
    exampleSolutionPublished = false;

    get studentExam(): StudentExam {
        return this._studentExam;
    }

    @Input()
    set studentExam(studentExam: StudentExam) {
        this._studentExam = studentExam;
        if (this.studentExamGradeInfoDTO) {
            this.studentExamGradeInfoDTO.studentExam = studentExam;
        }
        this.tryLoadPlagiarismCaseInfosForStudent();
    }

    /**
     * Grade info for current student's exam.
     */
    studentExamGradeInfoDTO: StudentExamWithGradeDTO;

    isGradingKeyCollapsed: boolean = true;
    isBonusGradingKeyCollapsed: boolean = true;

    @Input()
    instructorView = false;

    courseId: number;

    isTestRun = false;
    isTestExam = false;

    testRunConduction = false;
    testExamConduction = false;

    examWithOnlyIdAndStudentReviewPeriod: Exam;

    // Icons
    faFolderOpen = faFolderOpen;
    faInfoCircle = faInfoCircle;
    faPrint = faPrint;
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;

    exerciseInfos: Record<number, ExerciseInfo>;

    constructor(
        private route: ActivatedRoute,
        private serverDateService: ArtemisServerDateService,
        private themeService: ThemeService,
        private examParticipationService: ExamParticipationService,
        private plagiarismCasesService: PlagiarismCasesService,
    ) {}

    /**
     * Initialise the courseId from the current url
     */
    ngOnInit(): void {
        // flags required to display test runs correctly
        this.isTestRun = this.route.snapshot.url[1]?.toString() === 'test-runs';
        this.isTestExam = this.studentExam.exam!.testExam!;
        this.testRunConduction = this.isTestRun && this.route.snapshot.url[3]?.toString() === 'conduction';
        this.testExamConduction = this.isTestExam && !this.studentExam.submitted;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        if (!this.studentExam?.exam?.id) {
            throw new Error('studentExam.exam.id should be present to fetch grade info');
        }
        if (!this.studentExam?.user?.id) {
            throw new Error('studentExam.user.id should be present to fetch grade info');
        }

        if (this.isExamResultPublished()) {
            this.examParticipationService
                .loadStudentExamGradeInfoForSummary(this.courseId, this.studentExam.exam.id, this.studentExam.user.id)
                .subscribe((studentExamWithGrade: StudentExamWithGradeDTO) => {
                    studentExamWithGrade.studentExam = this.studentExam;
                    this.studentExamGradeInfoDTO = studentExamWithGrade;
                });
        }

        this.exampleSolutionPublished = !!this.studentExam.exam?.exampleSolutionPublicationDate && dayjs().isAfter(this.studentExam.exam.exampleSolutionPublicationDate);

        this.exerciseInfos = this.getExerciseInfos();

        this.setExamWithOnlyIdAndStudentReviewPeriod();
    }

    private tryLoadPlagiarismCaseInfosForStudent() {
        // If the exam has not yet ended, or we're only a few minutes after the end, we can assume that there are no plagiarism cases yet.
        // We should avoid trying to load them to reduce server load.
        if (this.studentExam?.exam?.endDate) {
            const endDateWithTimeExtension = dayjs(this.studentExam.exam.endDate).add(2, 'hours');
            if (dayjs().isBefore(endDateWithTimeExtension)) {
                return;
            }
        }

        const exerciseIds = this.studentExam?.exercises?.map((exercise) => exercise.id!);
        if (exerciseIds?.length && this.courseId) {
            this.plagiarismCasesService.getPlagiarismCaseInfosForStudent(this.courseId, exerciseIds).subscribe((res) => {
                this.plagiarismCaseInfos = res.body ?? {};
            });
        }
    }

    private isExamResultPublished() {
        const exam = this.studentExam.exam;
        return exam?.publishResultsDate && dayjs(exam.publishResultsDate).isBefore(this.serverDateService.now());
    }

    asProgrammingExercise(exercise: Exercise): ProgrammingExercise {
        return exercise as ProgrammingExercise;
    }

    get resultsPublished() {
        if (this.testRunConduction || this.testExamConduction) {
            return false;
        } else if (this.isTestRun || this.isTestExam) {
            return true;
        }
        return this.studentExam?.exam?.publishResultsDate && dayjs(this.studentExam.exam.publishResultsDate).isBefore(dayjs());
    }

    /**
     * called for exportPDF Button
     */
    printPDF() {
        this.expandExercisesAndGradingKeysBeforePrinting();
        setTimeout(() => this.themeService.print());
    }

    private expandExercisesAndGradingKeysBeforePrinting() {
        this.expandExercises();

        this.isGradingKeyCollapsed = false;
        this.isBonusGradingKeyCollapsed = false;
    }

    private expandExercises() {
        Object.entries(this.exerciseInfos).forEach((exerciseInfo: [string, ExerciseInfo]) => {
            exerciseInfo[1].isCollapsed = false;
        });
    }

    public generateLink(exercise: Exercise) {
        if (exercise?.studentParticipations?.length) {
            return ['/courses', this.courseId, `${exercise.type}-exercises`, exercise.id, 'participate', exercise.studentParticipations[0].id];
        }
        return undefined;
    }

    /**
     * @param exercise
     * returns the students' submission for the exercise, undefined if no participation could be found
     */
    getSubmissionForExercise(exercise: Exercise) {
        return exercise?.studentParticipations?.[0]?.submissions?.[0];
    }

    /**
     * @param exercise
     * returns the students' submission for the exercise, undefined if no participation could be found
     */
    getParticipationForExercise(exercise: Exercise) {
        return exercise.studentParticipations?.[0] || undefined;
    }

    /**
     * adds collapse control of exercise cards depending on exerciseId
     * @param exerciseId the exercise for which the submission should be collapsed
     */
    toggleCollapseExercise(exerciseId: number): void {
        this.exerciseInfos[exerciseId].isCollapsed = !this.exerciseInfos[exerciseId].isCollapsed;
    }

    /**
     * We only need to pass these values to the ComplaintInteractionComponent
     */
    setExamWithOnlyIdAndStudentReviewPeriod() {
        const exam = new Exam();
        exam.id = this.studentExam?.exam?.id;
        exam.examStudentReviewStart = this.studentExam?.exam?.examStudentReviewStart;
        exam.examStudentReviewEnd = this.studentExam?.exam?.examStudentReviewEnd;
        exam.course = this.studentExam?.exam?.course;
        this.examWithOnlyIdAndStudentReviewPeriod = exam;
    }

    /**
     * Used to decide whether to instantiate the ComplaintInteractionComponent. We always instantiate the component if
     * the review dates are set and the review start date has passed.
     */
    isAfterStudentReviewStart() {
        if (this.isTestRun || this.isTestExam) {
            return true;
        }
        if (this.studentExam?.exam?.examStudentReviewStart && this.studentExam.exam.examStudentReviewEnd) {
            return this.serverDateService.now().isAfter(this.studentExam.exam.examStudentReviewStart);
        }
        return false;
    }

    isBeforeStudentReviewEnd() {
        if (this.isTestRun || this.isTestExam) {
            return true;
        }
        if (this.studentExam?.exam?.examStudentReviewStart && this.studentExam.exam.examStudentReviewEnd) {
            return this.serverDateService.now().isBefore(this.studentExam.exam.examStudentReviewEnd);
        }
        return false;
    }

    private getExerciseInfos() {
        const exerciseInfos: Record<number, ExerciseInfo> = {};
        for (const exercise of this.studentExam?.exercises ?? []) {
            if (exercise.id === undefined) {
                console.error('Exercise id is undefined', exercise);
                continue;
            }
            exerciseInfos[exercise.id] = {
                icon: getIcon(exercise.type),
                isCollapsed: false,
                // achievedPercentage: this.getAchievedPercentageByExerciseId(exercise.id),
                // colorClass: this.getTextColorClassByExercise(exercise),
            };
        }
        return exerciseInfos;
    }

    protected readonly getIcon = getIcon;
}

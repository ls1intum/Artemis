import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeStep } from 'app/entities/grade-step.model';
import { GradeType } from 'app/entities/grading-scale.model';
import { CourseScoreCalculationService, ScoreType } from 'app/overview/course-score-calculation.service';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faChevronLeft, faPrint } from '@fortawesome/free-solid-svg-icons';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { GradeEditMode } from 'app/grading-system/base-grading-system/base-grading-system.component';
import { ThemeService } from 'app/core/theme/theme.service';

@Component({
    selector: 'jhi-grade-key-overview',
    templateUrl: './grading-key-overview.component.html',
    styleUrls: ['./grading-key-overview.scss'],
})
export class GradingKeyOverviewComponent implements OnInit {
    // Icons
    readonly faChevronLeft = faChevronLeft;
    readonly faPrint = faPrint;

    readonly GradeEditMode = GradeEditMode;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private gradingSystemService: GradingSystemService,
        private courseCalculationService: CourseScoreCalculationService,
        private navigationUtilService: ArtemisNavigationUtilService,
        private themeService: ThemeService,
    ) {}

    isExam = false;

    courseId?: number;
    examId?: number;

    title?: string;
    gradeSteps: GradeStep[] = [];
    studentGrade?: string;
    isBonus = false;

    ngOnInit(): void {
        // Note: due to lazy loading and router outlet, we use parent 2x here
        this.route.parent?.parent?.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            if (params['examId']) {
                this.examId = Number(params['examId']);
                this.isExam = true;
            }
            this.gradingSystemService.findGradeSteps(this.courseId, this.examId).subscribe((gradeSteps) => {
                if (gradeSteps) {
                    this.title = gradeSteps.title;
                    this.isBonus = gradeSteps.gradeType === GradeType.BONUS;
                    this.gradeSteps = this.gradingSystemService.sortGradeSteps(gradeSteps.gradeSteps);
                    if (gradeSteps.maxPoints !== undefined) {
                        if (!this.isExam) {
                            // calculate course max points based on exercises
                            const course = this.courseCalculationService.getCourse(this.courseId!);
                            const maxPoints = this.courseCalculationService.calculateTotalScores(course!.exercises!, course!).get(ScoreType.REACHABLE_POINTS);
                            this.gradingSystemService.setGradePoints(this.gradeSteps, maxPoints!);
                        } else {
                            // for exams the max points filed should equal the total max points (otherwise exams can't be started)
                            this.gradingSystemService.setGradePoints(this.gradeSteps, gradeSteps.maxPoints!);
                        }
                    }
                }
            });
        });
        this.route.parent?.parent?.queryParams.subscribe((queryParams) => {
            this.studentGrade = queryParams['grade'];
        });
    }

    /**
     * Navigates to the previous page (back button on the browser)
     */
    previousState() {
        const fallbackUrl = ['courses', this.courseId!.toString()];
        if (this.isExam) {
            fallbackUrl.push('exams', this.examId!.toString());
        } else {
            fallbackUrl.push('statistics');
        }
        this.navigationUtilService.navigateBack(fallbackUrl);
    }

    /**
     * Exports page as PDF
     */
    printPDF() {
        setTimeout(() => this.themeService.print());
    }

    /**
     * @see GradingSystemService.hasPointsSet
     */
    hasPointsSet(): boolean {
        return this.gradingSystemService.hasPointsSet(this.gradeSteps);
    }

    /**
     * @see GradeStepBoundsPipe.round
     */
    round(num?: number) {
        return GradeStepBoundsPipe.round(num);
    }
}

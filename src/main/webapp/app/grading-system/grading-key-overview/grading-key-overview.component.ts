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
        // Note: This component is used in multiple routes, so it can be lazy loaded. Also, courseId and examId can be
        // found on different levels of hierarchy tree (on the same level or a parent or a grandparent, etc.).
        this.courseId = Number(this.findParamInRouteHierarchy('courseId'));
        const examIdParam = this.findParamInRouteHierarchy('examId');
        if (examIdParam) {
            this.examId = Number(examIdParam);
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

        // Needed queryParam is available on this component so no need to traverse the hierarchy like params above.
        this.studentGrade = this.route.snapshot.queryParams['grade'];
    }

    /**
     * Checks router hierarchy to find a given paramKey, starting from the current ActivatedRouteSnapshot
     * and traversing the parents.
     * @param paramKey the desired key of route.snapshot.params
     * @private
     */
    private findParamInRouteHierarchy(paramKey: string): string | undefined {
        let currentRoute: ActivatedRoute | null = this.route;
        while (currentRoute) {
            const paramValue = currentRoute.snapshot.params[paramKey];
            if (paramValue !== undefined) {
                return paramValue;
            }
            currentRoute = currentRoute.parent;
        }
        return undefined;
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
     * Determines whether all grade steps have their lower and upper bounds set in absolute points
     */
    hasPointsSet(): boolean {
        for (const gradeStep of this.gradeSteps) {
            if (gradeStep.lowerBoundPoints == undefined || gradeStep.upperBoundPoints == undefined || gradeStep.upperBoundPoints === 0) {
                return false;
            }
        }
        return this.gradeSteps.length !== 0;
    }

    /**
     * @see GradeStepBoundsPipe.round
     */
    round(num?: number) {
        return GradeStepBoundsPipe.round(num);
    }
}

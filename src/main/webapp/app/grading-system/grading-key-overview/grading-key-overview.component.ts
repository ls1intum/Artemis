import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeStep } from 'app/entities/grade-step.model';
import { GradeType } from 'app/entities/grading-scale.model';
import { round } from 'app/shared/util/utils';
import { CourseScoreCalculationService, ScoreType } from 'app/overview/course-score-calculation.service';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faChevronLeft, faPrint } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-grade-key-overview',
    templateUrl: './grading-key-overview.component.html',
    styleUrls: ['./grading-key-overview.scss'],
})
export class GradingKeyOverviewComponent implements OnInit {
    // Icons
    faChevronLeft = faChevronLeft;
    faPrint = faPrint;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private gradingSystemService: GradingSystemService,
        private courseCalculationService: CourseScoreCalculationService,
        private navigationUtilService: ArtemisNavigationUtilService,
    ) {}

    isExam = false;

    courseId?: number;
    examId?: number;

    title?: string;
    gradeSteps: GradeStep[] = [];
    studentGrade?: string;
    isBonus = false;

    ngOnInit(): void {
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
        this.route.queryParams.subscribe((queryParams) => {
            this.studentGrade = queryParams['grade'];
        });
    }

    /**
     * Navigates to the previous page (back button on the browser)
     */
    previousState() {
        this.navigationUtilService.navigateBack(['courses', this.courseId!.toString(), 'statistics']);
    }

    /**
     * Exports page as PDF
     */
    printPDF() {
        setTimeout(() => window.print());
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
     * Rounds a number to two decimal places
     *
     * @param num the number to be rounded
     */
    round(num?: number) {
        if (num == undefined) {
            return;
        }
        return round(num, 2);
    }
}

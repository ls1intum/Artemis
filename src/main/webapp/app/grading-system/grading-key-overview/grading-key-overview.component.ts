import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { ArtemisNavigationUtilService, findParamInRouteHierarchy } from 'app/utils/navigation.utils';
import { faChevronLeft, faPrint } from '@fortawesome/free-solid-svg-icons';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { GradeEditMode } from 'app/grading-system/base-grading-system/base-grading-system.component';
import { ThemeService } from 'app/core/theme/theme.service';
import { BonusService } from 'app/grading-system/bonus/bonus.service';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { ScoresStorageService } from 'app/course/course-scores/scores-storage.service';
import { ScoreType } from 'app/shared/constants/score-type.constants';

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

    plagiarismGrade: string;
    noParticipationGrade: string;

    constructor(
        private route: ActivatedRoute,
        private gradingSystemService: GradingSystemService,
        private bonusService: BonusService,
        private scoresStorageService: ScoresStorageService,
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
    forBonus: boolean;

    ngOnInit(): void {
        // Note: This component is used in multiple routes, so it can be lazy loaded. Also, courseId and examId can be
        // found on different levels of hierarchy tree (on the same level or a parent or a grandparent, etc.).
        this.courseId = Number(findParamInRouteHierarchy(this.route, 'courseId'));
        const examIdParam = findParamInRouteHierarchy(this.route, 'examId');
        if (examIdParam) {
            this.examId = Number(examIdParam);
            this.isExam = true;
        }
        this.forBonus = !!this.route.snapshot.data['forBonus'];
        this.findGradeSteps(this.courseId, this.examId).subscribe((gradeSteps) => {
            if (gradeSteps) {
                this.title = gradeSteps.title;
                this.isBonus = gradeSteps.gradeType === GradeType.BONUS;
                this.gradeSteps = this.gradingSystemService.sortGradeSteps(gradeSteps.gradeSteps);
                this.plagiarismGrade = gradeSteps.plagiarismGrade;
                this.noParticipationGrade = gradeSteps.noParticipationGrade;
                if (gradeSteps.maxPoints !== undefined) {
                    if (!this.isExam) {
                        let maxPoints = 0;
                        const totalScoresForCourse = this.scoresStorageService.getStoredTotalScores(this.courseId!);
                        if (totalScoresForCourse) {
                            maxPoints = totalScoresForCourse[ScoreType.REACHABLE_POINTS];
                        }
                        this.gradingSystemService.setGradePoints(this.gradeSteps, maxPoints);
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

    private findGradeSteps(courseId: number, examId?: number): Observable<GradeStepsDTO | undefined> {
        if (!this.forBonus) {
            return this.gradingSystemService.findGradeSteps(courseId, examId);
        } else {
            // examId must be present if forBonus is true.
            return this.bonusService.findBonusForExam(courseId, examId!, true).pipe(
                map((bonusResponse) => {
                    const source = bonusResponse.body?.sourceGradingScale;
                    if (!source) {
                        return undefined;
                    }
                    return {
                        title: this.gradingSystemService.getGradingScaleTitle(source)!,
                        gradeType: source.gradeType,
                        gradeSteps: source.gradeSteps,
                        maxPoints: this.gradingSystemService.getGradingScaleMaxPoints(source),
                        plagiarismGrade: source.plagiarismGrade || GradingScale.DEFAULT_PLAGIARISM_GRADE,
                        noParticipationGrade: source.noParticipationGrade || GradingScale.DEFAULT_NO_PARTICIPATION_GRADE,
                        presentationsNumber: source.presentationsNumber,
                        presentationsWeight: source.presentationsWeight,
                    };
                }),
            );
        }
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

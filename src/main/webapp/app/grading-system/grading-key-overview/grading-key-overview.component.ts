import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeStep } from 'app/entities/grade-step.model';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faChevronLeft, faPrint } from '@fortawesome/free-solid-svg-icons';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { ThemeService } from 'app/core/theme/theme.service';
import { loadGradingKeyUrlParams } from 'app/grading-system/grading-key-overview/grading-key-helper';

@Component({
    selector: 'jhi-grade-key-overview',
    templateUrl: './grading-key-overview.component.html',
    styleUrls: ['./grading-key-overview.scss'],
})
export class GradingKeyOverviewComponent implements OnInit {
    readonly faChevronLeft = faChevronLeft;
    readonly faPrint = faPrint;

    plagiarismGrade: string;
    noParticipationGrade: string;

    constructor(
        private route: ActivatedRoute,
        private gradingSystemService: GradingSystemService,
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
        const { courseId, examId, forBonus, isExam, studentGrade } = loadGradingKeyUrlParams(this.route);

        this.courseId = courseId;
        this.examId = examId;
        this.forBonus = forBonus;
        this.isExam = isExam;
        this.studentGrade = studentGrade;
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

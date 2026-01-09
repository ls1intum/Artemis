import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { GradeStep } from 'app/assessment/shared/entities/grade-step.model';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { faChevronLeft, faPrint } from '@fortawesome/free-solid-svg-icons';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { loadGradingKeyUrlParams } from 'app/assessment/manage/grading/grading-key-overview/grading-key-helper';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { GradingKeyTableComponent } from 'app/assessment/manage/grading/grading-key/grading-key-table.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-grade-key-overview',
    templateUrl: './grading-key-overview.component.html',
    styleUrls: ['./grading-key-overview.scss'],
    imports: [TranslateDirective, GradingKeyTableComponent, FaIconComponent, ArtemisTranslatePipe],
})
export class GradingKeyOverviewComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private themeService = inject(ThemeService);

    readonly faChevronLeft = faChevronLeft;
    readonly faPrint = faPrint;

    plagiarismGrade: string;
    noParticipationGrade: string;

    isExam = false;

    courseId?: number;
    examId?: number;

    title?: string;
    gradeSteps: GradeStep[] = [];
    studentGradeOrBonusPointsOrGradeBonus?: string;
    isBonus = false;
    forBonus: boolean;

    ngOnInit(): void {
        const { courseId, examId, forBonus, isExam, studentGradeOrBonusPointsOrGradeBonus } = loadGradingKeyUrlParams(this.route);

        this.courseId = courseId;
        this.examId = examId;
        this.forBonus = forBonus;
        this.isExam = isExam;
        this.studentGradeOrBonusPointsOrGradeBonus = studentGradeOrBonusPointsOrGradeBonus;
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
    async printPDF() {
        await this.themeService.print();
    }
}

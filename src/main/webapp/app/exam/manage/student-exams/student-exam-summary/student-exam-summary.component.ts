import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExamResultSummaryComponent } from '../../../overview/summary/exam-result-summary.component';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { TumUiButtonDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPrint } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-student-exam-summary',
    template: `
        <div *titleBarTitle class="flex items-center">
            <h5 class="mb-0" jhiTranslate="artemisApp.exam.examSummary.examResults"></h5>
        </div>
        <ng-template titleBarActions>
            <button tumUiButton size="small" severity="primary" (click)="printPDF()">
                <fa-icon [icon]="faPrint" />
                <span class="title-bar-collapsible-label" jhiTranslate="artemisApp.exam.examSummary.exportPDF"></span>
            </button>
        </ng-template>
        <jhi-exam-participation-summary #summary [studentExam]="studentExam()!" [instructorView]="true" />
    `,
    imports: [ExamResultSummaryComponent, CourseTitleBarTitleDirective, CourseTitleBarActionsDirective, TumUiButtonDirective, FaIconComponent, TranslateDirective],
})
export class StudentExamSummaryComponent implements OnInit {
    private route = inject(ActivatedRoute);

    readonly studentExam = signal<StudentExam | undefined>(undefined);
    readonly summaryComponent = viewChild<ExamResultSummaryComponent>('summary');
    readonly faPrint = faPrint;

    printPDF(): void {
        void this.summaryComponent()?.printPDF();
    }

    /**
     * Initialize the studentExam
     */
    ngOnInit(): void {
        this.route.data.subscribe(({ studentExam: studentExamWithGrade }) => this.studentExam.set(studentExamWithGrade.studentExam));
    }
}

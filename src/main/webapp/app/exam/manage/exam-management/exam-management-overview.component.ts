import { Component, OnDestroy, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { SortService } from 'app/foundation/service/sort.service';
import { faFileAlt, faFileImport, faPlus } from '@fortawesome/free-solid-svg-icons';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExamStatusComponent } from '../exam-status/exam-status.component';
import {
    TumAetUiButtonComponent,
    TumAetUiButtonDirective,
    TumAetUiDialogComponent,
    TumAetUiEmptyStateComponent,
    TumAetUiTableDirective,
    TumAetUiTableSortEvent,
    TumAetUiTableSortableColumnComponent,
} from '@tumaet/ui-angular';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { ExamModeBadgeComponent } from 'app/exam/shared/exam-mode-badge/exam-mode-badge.component';
import { ExamManagementComponent } from 'app/exam/manage/exam-management/exam-management.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exam-management-overview',
    templateUrl: './exam-management-overview.component.html',
    imports: [
        ArtemisTranslatePipe,
        TranslateDirective,
        FaIconComponent,
        RouterLink,
        TumAetUiTableDirective,
        TumAetUiTableSortableColumnComponent,
        ExamStatusComponent,
        TumAetUiButtonDirective,
        TumAetUiButtonComponent,
        TumAetUiDialogComponent,
        TumAetUiEmptyStateComponent,
        ExamImportComponent,
        CourseTitleBarActionsDirective,
        CourseTitleBarTitleDirective,
        ExamModeBadgeComponent,
    ],
})
export class ExamManagementOverviewComponent implements OnDestroy {
    private examManagementComponent = inject(ExamManagementComponent);
    private sortService = inject(SortService);
    private router = inject(Router);

    readonly course = this.examManagementComponent.course;
    readonly exams = this.examManagementComponent.exams;

    readonly predicate = signal('id');
    readonly ascending = signal(true);
    readonly importDialogVisible = signal(false);
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faPlus = faPlus;
    faFileImport = faFileImport;
    faFileAlt = faFileAlt;

    /**
     * unsubscribe on component destruction
     */
    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Track the items on the Exams Table
     * @param _index the index in the table
     * @param exam the exam object to track
     */
    trackId(_index: number, exam: Exam): number | undefined {
        return exam.id;
    }

    onSortChange(event: TumAetUiTableSortEvent): void {
        this.predicate.set(event.field);
        this.ascending.set(event.order > 0);
        this.sortRows();
    }

    sortRows() {
        // sortByProperty sorts in place; re-set a new array reference so the signal notifies and the (zoneless) view re-renders.
        this.exams.set([...this.sortService.sortByProperty(this.exams(), this.predicate(), this.ascending())]);
    }

    /**
     * Opens the import dialog for an exam import
     */
    openImportModal() {
        this.importDialogVisible.set(true);
    }

    /**
     * Closes the import dialog and continues with the import of the exam the user chose
     * @param exam the exam chosen for the import
     */
    onExamSelected(exam: Exam) {
        this.importDialogVisible.set(false);
        void this.router.navigate(['/course-management', this.course().id, 'exams', 'import', exam.id]);
    }
}

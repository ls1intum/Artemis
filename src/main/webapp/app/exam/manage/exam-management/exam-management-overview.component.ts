import { Component, OnDestroy, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { SortService } from 'app/foundation/service/sort.service';
import { faFileImport, faPlus, faSort } from '@fortawesome/free-solid-svg-icons';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { ExamImportComponent, ExamImportDialogData } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { ExamStatusComponent } from '../exam-status/exam-status.component';
import { TumUiButtonComponent, TumUiButtonDirective } from '@tumaet/ui-angular';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { ExamModeBadgeComponent } from 'app/exam/shared/exam-mode-badge/exam-mode-badge.component';
import { ExamManagementComponent } from 'app/exam/manage/exam-management/exam-management.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exam-management-overview',
    templateUrl: './exam-management-overview.component.html',
    styleUrls: ['./exam-management-overview.component.scss'],
    imports: [
        ArtemisTranslatePipe,
        TranslateDirective,
        FaIconComponent,
        RouterLink,
        SortDirective,
        SortByDirective,
        ExamStatusComponent,
        TumUiButtonDirective,
        TumUiButtonComponent,
        CourseTitleBarActionsDirective,
        CourseTitleBarTitleDirective,
        ExamModeBadgeComponent,
    ],
})
export class ExamManagementOverviewComponent implements OnDestroy {
    private examManagementComponent = inject(ExamManagementComponent);
    private sortService = inject(SortService);
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);
    private router = inject(Router);

    readonly course = this.examManagementComponent.course;
    readonly exams = this.examManagementComponent.exams;

    predicate: string;
    ascending: boolean;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faFileImport = faFileImport;

    constructor() {
        this.predicate = 'id';
        this.ascending = true;
    }

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

    sortRows() {
        // sortByProperty sorts in place; re-set a new array reference so the signal notifies and the (zoneless) view re-renders.
        this.exams.set([...this.sortService.sortByProperty(this.exams(), this.predicate, this.ascending)]);
    }

    /**
     * Opens the import module for an exam import
     */
    openImportModal() {
        const dialogData: ExamImportDialogData = {
            subsequentExerciseGroupSelection: false,
        };

        const dialogRef = this.dialogService.open(ExamImportComponent, {
            header: this.translateService.instant('artemisApp.examManagement.importExam'),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            draggable: false,
            data: dialogData,
        });

        const importBaseRoute = ['/course-management', this.course().id, 'exams', 'import'];

        dialogRef?.onClose.subscribe((exam: Exam | undefined) => {
            if (exam) {
                importBaseRoute.push(exam.id);
                void this.router.navigate(importBaseRoute);
            }
        });
    }
}

import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { SortService } from 'app/shared/service/sort.service';
import { ExamInformationDTO } from 'app/exam/shared/entities/exam-information.model';
import dayjs from 'dayjs/esm';
import { EventManager } from 'app/shared/service/event-manager.service';
import { faClipboard, faEye, faFileImport, faListAlt, faPlus, faSort, faThList, faTimes, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { ExamImportComponent, ExamImportDialogData } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { ExamStatusComponent } from '../exam-status/exam-status.component';
import { CourseTitleBarTitleDirective } from 'app/core/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarActionsDirective } from 'app/core/course/shared/directives/course-title-bar-actions.directive';
import { CourseTitleBarTitleComponent } from 'app/core/course/shared/course-title-bar-title/course-title-bar-title.component';

@Component({
    selector: 'jhi-exam-management',
    templateUrl: './exam-management.component.html',
    styleUrls: ['./exam-management.component.scss'],
    imports: [
        TranslateDirective,
        DocumentationButtonComponent,
        FaIconComponent,
        RouterLink,
        SortDirective,
        SortByDirective,
        ExamStatusComponent,
        CourseTitleBarTitleDirective,
        CourseTitleBarActionsDirective,
        CourseTitleBarTitleComponent,
    ],
})
export class ExamManagementComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private courseService = inject(CourseManagementService);
    private examManagementService = inject(ExamManagementService);
    private eventManager = inject(EventManager);
    private alertService = inject(AlertService);
    private sortService = inject(SortService);
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);
    private router = inject(Router);

    readonly documentationType: DocumentationType = 'Exams';

    course: Course;
    exams: Exam[];
    predicate: string;
    ascending: boolean;
    eventSubscriber: Subscription;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    currentTime: dayjs.Dayjs;

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faFileImport = faFileImport;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faUser = faUser;
    faListAlt = faListAlt;
    faClipboard = faClipboard;
    faThList = faThList;

    constructor() {
        this.predicate = 'id';
        this.ascending = true;
    }

    /**
     * Initialize the course and all exams when this view is initialized.
     * Subscribes to 'examListModification' event.
     * @see registerChangeInExams
     */
    ngOnInit(): void {
        this.courseService.find(Number(this.route.snapshot.paramMap.get('courseId'))).subscribe({
            next: (res: HttpResponse<Course>) => {
                this.course = res.body!;
                this.loadAllExamsForCourse();
                this.registerChangeInExams();
                this.currentTime = dayjs();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    /**
     * unsubscribe on component destruction
     */
    ngOnDestroy() {
        if (this.eventSubscriber !== undefined) {
            this.eventManager.destroy(this.eventSubscriber);
        }
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Load all exams for a course.
     */
    loadAllExamsForCourse() {
        this.examManagementService.findAllExamsForCourse(this.course.id!).subscribe({
            next: (res: HttpResponse<Exam[]>) => {
                this.exams = res.body!;
                this.exams.forEach((exam) => {
                    this.examManagementService
                        .getLatestIndividualEndDateOfExam(this.course.id!, exam.id!)
                        .subscribe(
                            (examInformationDTORes: HttpResponse<ExamInformationDTO>) => (exam.latestIndividualEndDate = examInformationDTORes.body!.latestIndividualEndDate),
                        );
                });
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    /**
     * Subscribes to 'examListModification' events
     */
    registerChangeInExams() {
        this.eventSubscriber = this.eventManager.subscribe('examListModification', () => {
            this.loadAllExamsForCourse();
            this.currentTime = dayjs();
        });
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
        this.sortService.sortByProperty(this.exams, this.predicate, this.ascending);
    }

    examHasFinished(exam: Exam): boolean {
        if (exam.latestIndividualEndDate) {
            return exam.latestIndividualEndDate.isBefore(dayjs());
        }
        return false;
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

        const importBaseRoute = ['/course-management', this.course.id, 'exams', 'import'];

        dialogRef?.onClose.subscribe((exam: Exam | undefined) => {
            if (exam) {
                importBaseRoute.push(exam.id);
                this.router.navigate(importBaseRoute);
            }
        });
    }
}

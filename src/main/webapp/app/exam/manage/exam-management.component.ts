import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exam } from 'app/entities/exam.model';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { SortService } from 'app/shared/service/sort.service';
import { ExamInformationDTO } from 'app/entities/exam-information.model';
import dayjs from 'dayjs/esm';
import { EventManager } from 'app/core/util/event-manager.service';
import { faClipboard, faEye, faListAlt, faPlus, faSort, faThList, faTimes, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';

@Component({
    selector: 'jhi-exam-management',
    templateUrl: './exam-management.component.html',
    styleUrls: ['./exam-management.component.scss'],
})
export class ExamManagementComponent implements OnInit, OnDestroy {
    documentationType = DocumentationType.Exams;

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
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faUser = faUser;
    faListAlt = faListAlt;
    faClipboard = faClipboard;
    faThList = faThList;

    constructor(
        private route: ActivatedRoute,
        private courseService: CourseManagementService,
        private examManagementService: ExamManagementService,
        private eventManager: EventManager,
        private accountService: AccountService,
        private alertService: AlertService,
        private sortService: SortService,
        private modalService: NgbModal,
        private router: Router,
    ) {
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
        if (!this.eventSubscriber === undefined) {
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
     * @param index {number}
     * @param item {Exam}
     */
    trackId(index: number, item: Exam): number | undefined {
        return item.id;
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
        const examImportModalRef = this.modalService.open(ExamImportComponent, {
            size: 'lg',
            backdrop: 'static',
        });
        // The Exercise Group selection is performed within the exam-update.component afterwards
        examImportModalRef.componentInstance.subsequentExerciseGroupSelection = false;

        const importBaseRoute = ['/course-management', this.course.id, 'exams', 'import'];

        examImportModalRef.result.then((exam: Exam) => {
            importBaseRoute.push(exam.id);
            this.router.navigate(importBaseRoute);
        });
    }
}

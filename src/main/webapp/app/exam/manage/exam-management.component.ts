import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { Subject } from 'rxjs';
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

@Component({
    selector: 'jhi-exam-management',
    templateUrl: './exam-management.component.html',
})
export class ExamManagementComponent implements OnInit, OnDestroy {
    course: Course;
    exams: Exam[];
    predicate: string;
    ascending: boolean;
    eventSubscriber: Subscription;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

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
    ) {
        this.predicate = 'id';
        this.ascending = true;
    }

    /**
     * Initialize the course and all exams when this view is initialised.
     * Subscribes to 'examListModification' event.
     * @see registerChangeInExams
     */
    ngOnInit(): void {
        this.courseService.find(Number(this.route.snapshot.paramMap.get('courseId'))).subscribe(
            (res: HttpResponse<Course>) => {
                this.course = res.body!;
                this.loadAllExamsForCourse();
                this.registerChangeInExams();
            },
            (res: HttpErrorResponse) => onError(this.alertService, res),
        );
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
        this.examManagementService.findAllExamsForCourse(this.course.id!).subscribe(
            (res: HttpResponse<Exam[]>) => {
                this.exams = res.body!;
                this.exams.forEach((exam) => {
                    this.examManagementService
                        .getLatestIndividualEndDateOfExam(this.course.id!, exam.id!)
                        .subscribe(
                            (examInformationDTORes: HttpResponse<ExamInformationDTO>) => (exam.latestIndividualEndDate = examInformationDTORes.body!.latestIndividualEndDate),
                        );
                });
            },
            (res: HttpErrorResponse) => onError(this.alertService, res),
        );
    }

    /**
     * Subscribes to 'examListModification' events
     */
    registerChangeInExams() {
        this.eventSubscriber = this.eventManager.subscribe('examListModification', () => {
            this.loadAllExamsForCourse();
        });
    }

    /**
     * Function is called when the delete button is pressed for an exam
     * @param examId Id to be deleted
     */
    deleteExam(examId: number): void {
        this.examManagementService.delete(this.course.id!, examId).subscribe(
            () => {
                this.dialogErrorSource.next('');
                this.exams = this.exams.filter((exam) => exam.id !== examId);
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
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
}

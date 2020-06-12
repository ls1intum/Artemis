import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { JhiEventManager } from 'ng-jhipster';
import { Subscription } from 'rxjs/Subscription';
import { Subject } from 'rxjs';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exam } from 'app/entities/exam.model';
import * as moment from 'moment';

@Component({
    selector: 'jhi-exam-management',
    templateUrl: './exam-management.component.html',
    styleUrls: ['./exam-management.scss'],
})
export class ExamManagementComponent implements OnInit, OnDestroy {
    courseId: number;
    predicate: string;
    exams: Exam[];
    eventSubscriber: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;

    constructor(private route: ActivatedRoute, private examManagementService: ExamManagementService, private eventManager: JhiEventManager) {
        this.predicate = 'id';
    }

    loadAll() {
        this.exams = this.examManagementService.findAllForCourse(this.courseId);
        console.log('Exam array size: '.concat(String(this.exams.length)));
    }

    /**
     * subscribes to courseListModification event
     */
    registerChangeInExams() {
        this.eventSubscriber = this.eventManager.subscribe('examListModification', () => {
            console.log('reloading');
            this.loadAll();
        });
    }

    /**
     * Deletes the exam
     * @param examId id the course that will be deleted
     */
    deleteExam(examId: number) {
        console.log('Delete pressed');
        this.examManagementService.delete(this.courseId, examId);
        this.eventManager.broadcast({
            name: 'examListModification',
            content: 'Deleted an exam',
        });
        this.dialogErrorSource.next('');
        // also delete from server
    }

    trackId(index: number, item: Exam) {
        return item.id;
    }

    /**
     * Initialize the courseId and all exams
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.loadAll();
        this.registerChangeInExams();
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
}

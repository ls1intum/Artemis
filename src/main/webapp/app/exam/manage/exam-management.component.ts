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
        // Mock Values
        const exam1 = new Exam();
        exam1.id = 1;
        exam1.title = 'EIST Exam SS 2020';
        exam1.visibleDate = moment({ year: 2020, month: 6, day: 6, hour: 11, m: 45, s: 0, ms: 0 });
        exam1.startDate = moment({ year: 2020, month: 6, day: 6, hour: 12, m: 0, s: 0, ms: 0 });
        exam1.endDate = moment({ year: 2020, month: 6, day: 6, hour: 14, m: 0, s: 0, ms: 0 });
        exam1.registeredUsers = 1186;
        exam1.isAtLeastInstructor = true;
        exam1.isAtLeastTutor = true;
        const exam2 = new Exam();
        exam2.id = 2;
        exam2.title = 'EIST Repeat Exam SS 2020';
        exam2.visibleDate = moment({ year: 2020, month: 8, day: 12, hour: 10, m: 45, s: 0, ms: 0 });
        exam2.startDate = moment({ year: 2020, month: 8, day: 12, hour: 11, m: 0, s: 0, ms: 0 });
        exam2.endDate = moment({ year: 2020, month: 8, day: 12, hour: 13, m: 0, s: 0, ms: 0 });
        exam2.registeredUsers = 419;
        exam2.isAtLeastTutor = true;
        this.exams = [exam1, exam2];
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
        this.exams.filter((t) => t.id !== examId);
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

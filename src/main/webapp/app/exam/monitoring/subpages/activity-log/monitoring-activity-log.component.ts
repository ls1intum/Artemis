import { Component, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from '../../../manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ExamMonitoringService } from 'app/exam/monitoring/exam-monitoring.service';
import { faListAlt } from '@fortawesome/free-solid-svg-icons';
import { ExamActionService } from 'app/exam/monitoring/exam-action.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-monitoring-activity-log',
    templateUrl: './monitoring-activity-log.component.html',
    styleUrls: ['./monitoring-activity-log.component.scss'],
})
export class MonitoringActivityLogComponent implements OnInit, OnDestroy {
    // Subscriptions
    private routeSubscription?: Subscription;
    private examSubscription?: Subscription;
    // Exam
    examId: number;
    courseId: number;
    exam: Exam;

    // Table columns
    readonly columns = [
        { prop: 'studentExamId', minWidth: 150, width: 200, maxWidth: 200 },
        { prop: 'timestamp', minWidth: 150, width: 200, template: 'timestampRef' },
        { prop: 'type', minWidth: 150, width: 200 },
        { prop: 'detail', minWidth: 250, width: 300, template: 'detailRef' },
    ];

    faListAlt = faListAlt;

    constructor(
        private route: ActivatedRoute,
        private examManagementService: ExamManagementService,
        private examMonitoringService: ExamMonitoringService,
        public examActionService: ExamActionService,
        public artemisDatePipe: ArtemisDatePipe,
    ) {}

    ngOnInit() {
        this.routeSubscription = this.route.parent?.params.subscribe((params) => {
            this.examId = Number(params['examId']);
            this.courseId = Number(params['courseId']);
        });

        this.examSubscription = this.examMonitoringService.getExamBehaviorSubject(this.examId)?.subscribe((exam) => {
            this.exam = exam!;
        });
    }

    ngOnDestroy() {
        this.examSubscription?.unsubscribe();
        this.routeSubscription?.unsubscribe();
    }
}

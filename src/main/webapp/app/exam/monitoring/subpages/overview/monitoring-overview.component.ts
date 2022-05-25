import { Component, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from '../../../manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ExamMonitoringService } from 'app/exam/monitoring/exam-monitoring.service';
import { faListAlt } from '@fortawesome/free-solid-svg-icons';
import { ExamMonitoringWebsocketService } from 'app/exam/monitoring/exam-monitoring-websocket.service';
import { ExamAction, ExamActivity } from 'app/entities/exam-user-activity.model';

@Component({
    selector: 'jhi-monitoring-overview',
    templateUrl: './monitoring-overview.component.html',
    styleUrls: ['./monitoring-overview.component.scss'],
})
export class MonitoringOverviewComponent implements OnInit, OnDestroy {
    // Subscriptions
    private routeSubscription?: Subscription;
    private examSubscription?: Subscription;
    private examMonitoringSubscription?: Subscription;

    // Exam
    examId: number;
    courseId: number;
    exam?: Exam;

    // Exam Activity
    examActivities: ExamActivity[];

    // Exam Actions
    examActions: ExamAction[];

    faListAlt = faListAlt;

    constructor(
        private route: ActivatedRoute,
        private examManagementService: ExamManagementService,
        private examMonitoringService: ExamMonitoringService,
        private examMonitoringWebsocketService: ExamMonitoringWebsocketService,
    ) {}

    ngOnInit() {
        this.routeSubscription = this.route.parent?.params.subscribe((params) => {
            this.examId = parseInt(params['examId'], 10);
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.examSubscription = this.examMonitoringService.getExamBehaviorSubject(this.examId)?.subscribe((exam) => {
            this.exam = exam;

            this.examMonitoringSubscription = this.examMonitoringWebsocketService.subscribeForExamActivities(this.exam!).subscribe((examActivities) => {
                this.examActivities = examActivities;
                this.examActions = examActivities.map((activity) => activity.examActions).flat();
            });
        });
    }

    ngOnDestroy(): void {
        this.examSubscription?.unsubscribe();
        this.routeSubscription?.unsubscribe();
        this.examMonitoringSubscription?.unsubscribe();
    }
}

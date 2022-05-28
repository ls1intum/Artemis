import { Component, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from '../../../manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ExamMonitoringService } from 'app/exam/monitoring/exam-monitoring.service';
import { faListAlt } from '@fortawesome/free-solid-svg-icons';
import { ExamMonitoringWebsocketService } from 'app/exam/monitoring/exam-monitoring-websocket.service';
import { EndedExamAction, ExamAction, StartedExamAction } from 'app/entities/exam-user-activity.model';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-monitoring-overview',
    templateUrl: './monitoring-overview.component.html',
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

    // Exam Actions
    examActions: ExamAction[] = [];

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

            this.examMonitoringSubscription = this.examMonitoringWebsocketService.subscribeForLatestExamAction(this.exam!).subscribe((examAction) => {
                if (examAction) {
                    this.examActions = [...this.examActions, examAction];
                }
            });
            this.examActions = this.createSampleActions();
        });
    }

    ngOnDestroy(): void {
        this.examSubscription?.unsubscribe();
        this.routeSubscription?.unsubscribe();
        this.examMonitoringSubscription?.unsubscribe();
    }

    createSampleActions(): ExamAction[] {
        const action = new StartedExamAction(5);
        action.timestamp = dayjs().add(1, 'hour');
        return [new StartedExamAction(5), new EndedExamAction(), action];
    }
}

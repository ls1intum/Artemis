import { Component, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from '../../../manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ExamMonitoringService } from 'app/exam/monitoring/exam-monitoring.service';
import { faListAlt } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-monitoring-overview',
    templateUrl: './monitoring-overview.component.html',
})
export class MonitoringOverviewComponent implements OnInit, OnDestroy {
    // Subscriptions
    private routeSubscription?: Subscription;
    private examSubscription?: Subscription;
    // Exam
    examId: number;
    courseId: number;
    exam?: Exam;

    faListAlt = faListAlt;

    constructor(private route: ActivatedRoute, private examManagementService: ExamManagementService, private examMonitoringService: ExamMonitoringService) {}

    ngOnInit() {
        this.routeSubscription = this.route.parent?.params.subscribe((params) => {
            this.examId = Number(params['examId']);
            this.courseId = Number(params['courseId']);
        });

        this.examSubscription = this.examMonitoringService.getExamBehaviorSubject(this.examId)?.subscribe((exam) => {
            this.exam = exam;
        });
    }

    ngOnDestroy() {
        this.examSubscription?.unsubscribe();
        this.routeSubscription?.unsubscribe();
    }
}

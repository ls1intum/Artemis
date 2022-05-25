import { AfterViewInit, Component, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from '../../../manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ExamMonitoringService } from 'app/exam/monitoring/exam-monitoring.service';

@Component({
    selector: 'jhi-monitoring-overview',
    templateUrl: './monitoring-overview.component.html',
    styleUrls: ['./monitoring-overview.component.scss', '../monitoring-card.component.scss'],
})
export class MonitoringOverviewComponent implements OnInit, OnChanges, OnDestroy, AfterViewInit {
    // Subscriptions
    private routeSubscription?: Subscription;
    private examSubscription?: Subscription;

    examId: number;
    exam?: Exam;

    constructor(private route: ActivatedRoute, private examManagementService: ExamManagementService, private examMonitoringService: ExamMonitoringService) {}

    ngOnInit() {
        this.routeSubscription = this.route.params.subscribe((params) => {
            this.examId = parseInt(params['examId'], 10);
        });

        this.examSubscription = this.examMonitoringService.getExamBehaviorSubject(this.examId)?.subscribe((exam) => {
            this.exam = exam;
        });
    }

    ngAfterViewInit(): void {}

    ngOnChanges(changes: SimpleChanges): void {
        throw new Error('Method not implemented.');
    }

    ngOnDestroy(): void {
        this.routeSubscription?.unsubscribe();
        this.examSubscription?.unsubscribe();
    }
}

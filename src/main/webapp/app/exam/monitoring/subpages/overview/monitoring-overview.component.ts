import { AfterViewInit, Component, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from '../../../manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ExamMonitoringService } from 'app/exam/monitoring/exam-monitoring.service';
import { faListAlt } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-monitoring-overview',
    templateUrl: './monitoring-overview.component.html',
    styleUrls: ['./monitoring-overview.component.scss'],
})
export class MonitoringOverviewComponent implements OnInit, OnChanges, OnDestroy, AfterViewInit {
    // Subscriptions
    private routeSubscription?: Subscription;
    private examSubscription?: Subscription;

    examId: number;
    courseId: number;
    exam?: Exam;

    faListAlt = faListAlt;

    constructor(private route: ActivatedRoute, private examManagementService: ExamManagementService, private examMonitoringService: ExamMonitoringService) {}

    ngOnInit() {
        this.routeSubscription = this.route.parent?.params.subscribe((params) => {
            this.examId = parseInt(params['examId'], 10);
            this.courseId = parseInt(params['courseId'], 10);
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
        this.examSubscription?.unsubscribe();
        this.routeSubscription?.unsubscribe();
    }
}

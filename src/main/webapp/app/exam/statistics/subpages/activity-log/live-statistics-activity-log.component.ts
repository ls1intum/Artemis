import { Component, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ExamLiveStatisticsService } from 'app/exam/statistics/exam-live-statistics.service';
import { ExamActionService } from 'app/exam/statistics/exam-action.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-live-statistics-activity-log',
    templateUrl: './live-statistics-activity-log.component.html',
    styleUrls: ['./live-statistics-activity-log.component.scss'],
})
export class LiveStatisticsActivityLogComponent implements OnInit, OnDestroy {
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
        { prop: 'type', minWidth: 150, width: 200, template: 'typeRef' },
        { prop: 'detail', minWidth: 250, width: 300, template: 'detailRef' },
    ];

    constructor(
        private route: ActivatedRoute,
        private examLiveStatisticsService: ExamLiveStatisticsService,
        public examActionService: ExamActionService,
        public artemisDatePipe: ArtemisDatePipe,
    ) {}

    ngOnInit() {
        this.routeSubscription = this.route.parent?.params.subscribe((params) => {
            this.examId = Number(params['examId']);
            this.courseId = Number(params['courseId']);
        });

        this.examSubscription = this.examLiveStatisticsService.getExamBehaviorSubject(this.examId)?.subscribe((exam) => {
            this.exam = exam!;
        });
    }

    ngOnDestroy() {
        this.examSubscription?.unsubscribe();
        this.routeSubscription?.unsubscribe();
    }
}

import { Component, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ExamLiveStatisticsService } from 'app/exam/statistics/exam-live-statistics.service';
import { faListAlt } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-live-statistics-overview',
    templateUrl: './live-statistics-overview.component.html',
})
export class LiveStatisticsOverviewComponent implements OnInit, OnDestroy {
    // Subscriptions
    private routeSubscription?: Subscription;
    private examSubscription?: Subscription;
    // Exam
    examId: number;
    courseId: number;
    exam?: Exam;

    faListAlt = faListAlt;

    constructor(private route: ActivatedRoute, private examLiveStatisticsService: ExamLiveStatisticsService) {}

    ngOnInit() {
        this.routeSubscription = this.route.parent?.params.subscribe((params) => {
            this.examId = Number(params['examId']);
            this.courseId = Number(params['courseId']);
        });

        this.examSubscription = this.examLiveStatisticsService.getExamBehaviorSubject(this.examId)?.subscribe((exam) => {
            this.exam = exam;
        });
    }

    ngOnDestroy() {
        this.examSubscription?.unsubscribe();
        this.routeSubscription?.unsubscribe();
    }
}

import { Component, OnInit, OnChanges } from '@angular/core';
import { StatisticsService } from 'app/admin/statistics/statistics.service';
import { SPAN_PATTERN } from 'app/app.constants';

@Component({
    selector: 'jhi-statistics',
    templateUrl: './statistics.component.html',
})
export class JhiStatisticsComponent implements OnInit, OnChanges {
    activities: any[] = [];
    spanPattern = SPAN_PATTERN;
    userSpan = 7;
    activeUserSpan = 7;
    submissionSpan = 7;
    releasedExerciseSpan = 7;
    exerciseDeadlineSpan = 7;
    loggedInUsers = 0;
    activeUsers = 0;
    totalSubmissions = 0;
    releasedExercises = 0;
    exerciseDeadlines = 0;

    constructor(private service: StatisticsService) {}

    showActivity(activity: any) {
        let existingActivity = false;
        for (let index = 0; index < this.activities.length; index++) {
            if (this.activities[index].sessionId === activity.sessionId) {
                existingActivity = true;
                if (activity.page === 'logout') {
                    this.activities.splice(index, 1);
                } else {
                    this.activities[index] = activity;
                }
            }
        }
        if (!existingActivity && activity.page !== 'logout') {
            this.activities.push(activity);
        }
    }

    ngOnInit() {
        this.onChangedUserSpan();
        this.onChangedActiveUserSpan();
        this.onChangedSubmissionSpan();
        this.onChangedReleasedExerciseSpan();
        this.onChangedExerciseDeadlineSpan();
    }

    ngOnChanges(): void {
        this.service.getloggedUsers(this.userSpan).subscribe((res: number) => {
            this.loggedInUsers = res;
        });
    }

    onChangedUserSpan(): void {
        this.service.getloggedUsers(this.userSpan).subscribe((res: number) => {
            this.loggedInUsers = res;
        });
    }
    onChangedActiveUserSpan(): void {
        this.service.getActiveUsers(this.activeUserSpan).subscribe((res: number) => {
            this.activeUsers = res;
        });
    }
    onChangedSubmissionSpan(): void {
        this.service.getTotalSubmissions(this.submissionSpan).subscribe((res: number) => {
            this.totalSubmissions = res;
        });
    }

    onChangedReleasedExerciseSpan(): void {
        this.service.getReleasedExercises(this.releasedExerciseSpan).subscribe((res: number) => {
            this.releasedExercises = res;
        });
    }

    onChangedExerciseDeadlineSpan(): void {
        this.service.getExerciseDeadlines(this.exerciseDeadlineSpan).subscribe((res: number) => {
            this.exerciseDeadlines = res;
        });
    }
}

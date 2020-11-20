import { Component, OnDestroy, OnInit, OnChanges } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { StatisticsService } from 'app/admin/statistics/statistics.service';
import { ModelingStatistic } from 'app/entities/modeling-statistic.model';
import { HttpResponse } from '@angular/common/http';
import { SPAN_PATTERN } from 'app/app.constants';
import { FormsModule } from '@angular/forms';

@Component({
    selector: 'jhi-statistics',
    templateUrl: './statistics.component.html',
})
export class JhiStatisticsComponent implements OnInit, OnDestroy, OnChanges {
    activities: any[] = [];
    spanPattern = SPAN_PATTERN;
    userSpan = 11;
    activeUserSpan = 11;
    submissionSpan = 11;
    loggedInUsers = 0;
    activeUsers = 0;
    totalSubmissions = 0;

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
        // this.trackerService.subscribe('/topic/tracker');
        // this.trackerService.receive('/topic/tracker').subscribe((activity: any) => {
        //    this.showActivity(activity);
        // });
        this.onChangedUserSpan();
        this.onChangedActiveUserSpan();
        this.onChangedSubmissionSpan();
        console.log('component:');
        console.log(this.loggedInUsers);
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

    ngOnDestroy() {
        // this.trackerService.unsubscribe('/topic/tracker');
    }
}

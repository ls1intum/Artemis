import { Component, OnDestroy, OnInit } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { StatisticsService } from 'app/admin/statistics/statistics.service';
import { ModelingStatistic } from 'app/entities/modeling-statistic.model';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-statistics',
    templateUrl: './statistics.component.html',
})
export class JhiStatisticsComponent implements OnInit, OnDestroy {
    activities: any[] = [];
    loggedInUsers: number = 1;

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
        const span = 7;
        this.service.getloggedUsers(span).subscribe((res: HttpResponse<number>) => {
            this.loggedInUsers = res.body!;
        });
        console.log(this.loggedInUsers);
    }

    ngOnDestroy() {
        // this.trackerService.unsubscribe('/topic/tracker');
    }
}

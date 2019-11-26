import { Component, OnDestroy, OnInit } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

@Component({
    selector: 'jhi-tracker',
    templateUrl: './tracker.component.html',
})
export class JhiTrackerComponent implements OnInit, OnDestroy {
    activities: any[] = [];
    disconnected = true;
    onConnected: () => void;
    onDisconnected: () => void;

    constructor(private trackerService: JhiWebsocketService) {}

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
        this.trackerService.subscribe('/topic/tracker');
        this.trackerService.receive('/topic/tracker').subscribe((activity: any) => {
            this.showActivity(activity);
        });

        // listen to connect / disconnect events
        this.onConnected = () => {
            this.disconnected = false;
        };
        this.trackerService.bind('connect', () => {
            this.onConnected();
        });
        this.onDisconnected = () => {
            this.disconnected = true;
        };
        this.trackerService.bind('disconnect', () => {
            this.onDisconnected();
        });
    }

    ngOnDestroy() {
        this.trackerService.unsubscribe('/topic/tracker');
    }
}

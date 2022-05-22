import { AfterViewInit, Component, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';

@Component({
    selector: 'jhi-monitoring-activity-log',
    templateUrl: './monitoring-activity-log.component.html',
    styleUrls: ['./monitoring-activity-log.component.scss'],
})
export class MonitoringActivityLogComponent implements OnInit, OnChanges, OnDestroy, AfterViewInit {
    constructor() {}

    ngOnChanges(changes: SimpleChanges): void {
        throw new Error('Method not implemented.');
    }

    ngOnInit() {}

    ngAfterViewInit(): void {}

    ngOnDestroy(): void {}
}

import { AfterViewInit, Component, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
@Component({
    selector: 'jhi-monitoring-overview',
    templateUrl: './monitoring-overview.component.html',
    styleUrls: ['./monitoring-overview.component.scss'],
})
export class MonitoringOverviewComponent implements OnInit, OnChanges, OnDestroy, AfterViewInit {
    constructor() {}

    ngOnChanges(changes: SimpleChanges): void {
        throw new Error('Method not implemented.');
    }

    ngOnInit() {}

    ngAfterViewInit(): void {}

    ngOnDestroy(): void {}
}

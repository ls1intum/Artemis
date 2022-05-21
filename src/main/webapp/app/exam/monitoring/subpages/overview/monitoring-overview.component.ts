import { AfterViewInit, Component, OnChanges, OnDestroy, OnInit, SimpleChanges, TemplateRef, ViewChild } from '@angular/core';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/overview/tab-bar';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-monitoring-overview',
    templateUrl: './monitoring-overview.component.html',
    styleUrls: ['./monitoring-overview.component.scss'],
})
export class MonitoringOverviewComponent implements OnInit, OnChanges, OnDestroy, AfterViewInit, BarControlConfigurationProvider {
    // The extracted controls' template from our template to be rendered in the top bar of "CourseOverviewComponent"
    @ViewChild('controls', { static: false }) private controls: TemplateRef<any>;
    // Provides the control configuration to be read and used by "ExamMonitoringComponent"
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
        useIndentation: true,
    };

    constructor() {}

    ngOnChanges(changes: SimpleChanges): void {
        throw new Error('Method not implemented.');
    }

    ngOnInit() {}

    ngAfterViewInit(): void {
        // Send our controls' template to parent, so it will be rendered in the top bar
        if (this.controls) {
            this.controlConfiguration.subject!.next(this.controls);
        }
    }

    ngOnDestroy(): void {}
}

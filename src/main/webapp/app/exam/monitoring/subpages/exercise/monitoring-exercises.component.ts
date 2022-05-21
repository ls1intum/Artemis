import { AfterViewInit, Component, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';

@Component({
    selector: 'jhi-monitoring-exercises',
    templateUrl: './monitoring-exercise.component.html',
    styleUrls: ['./monitoring-exercises.component.scss'],
})
export class MonitoringExercisesComponent implements OnInit, OnChanges, OnDestroy, AfterViewInit {
    constructor() {}

    ngOnChanges(changes: SimpleChanges): void {
        throw new Error('Method not implemented.');
    }

    ngOnInit() {}

    ngAfterViewInit(): void {}

    ngOnDestroy(): void {}
}

import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-working-time-change',
    templateUrl: './working-time-change.component.html',
})
export class WorkingTimeChangeComponent {
    @Input() oldWorkingTime: number;
    @Input() newWorkingTime: number;
}

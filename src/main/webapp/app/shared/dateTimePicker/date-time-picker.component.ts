import { Component, Input, OnInit } from '@angular/core';
import { isMoment, Moment } from 'moment';

@Component({
    selector: 'jhi-date-time-picker',
    template: `
        <label class="form-control-label">{{ labelName }}</label>
        <div class="d-flex">
            <label class="form-control" (click)="toggled = !toggled">{{ entity[field] | formatDate }}</label>
        </div>
        <owl-date-time-inline
            *ngIf="toggled"
            startAt="{{entity[field]}}"
            [(ngModel)]="entity[field]"
            name="datePicker"
        ></owl-date-time-inline>
    `
})

// TODO support setting a date to null
// TODO use the popup version of the date time picker
export class FormDateTimePickerComponent implements OnInit {
    toggled: boolean;
    @Input()
    labelName: string;
    @Input()
    entity: any;
    @Input()
    field: string;

    ngOnInit() {
        this.toggled = false;
        // convert moment to date, because owl-date-time only works correctly with date objects
        if (isMoment(this.entity[this.field])) {
            this.entity[this.field] = (this.entity[this.field] as Moment).toDate();
        }
    }
}

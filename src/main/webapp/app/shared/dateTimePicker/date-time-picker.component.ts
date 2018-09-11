import { Component, Input, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-date-time-picker',
    template: `
      <label class="form-control-label">{{labelName}}</label>
      <div class="d-flex" >
        <label class="form-control" (click)="toggled = !toggled">{{ entity[field] | formatDate}}</label>
      </div>
      <owl-date-time-inline *ngIf=toggled startAt="{{entity[field]}}"[(ngModel)]="entity[field]" name="datePicker"></owl-date-time-inline>
  `
})
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
    }
}

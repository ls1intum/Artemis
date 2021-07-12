import { Component, Output, EventEmitter } from '@angular/core';

@Component({
    selector: 'jhi-slide-toggle',
    template: `
        <!-- Default switch -->
        <div class="form-check form-switch">
            <input type="checkbox" class="form-check-input" id="flexSwitchCheckDefault" [(ngModel)]="checked" (change)="getCheckedFlag()" />
            <label class="form-check-label" for="flexSwitchCheckDefault"></label>
        </div>
        <label *ngIf="checked === false || checked === undefined" jhiTranslate="artemisApp.exercise.gradingInstructions"> Grading Instructions</label>
        <label *ngIf="checked === true" jhiTranslate="artemisApp.exercise.structuredGradingInstructions"> Structured Grading Instructions</label>
    `,
})
export class SlideToggleComponent {
    @Output() checkedEmitter = new EventEmitter<boolean>();
    checked: boolean;

    constructor() {}

    getCheckedFlag() {
        this.checkedEmitter.emit(this.checked);
    }
}

import { Component, Output, EventEmitter } from '@angular/core';

@Component({
    selector: 'jhi-slide-toggle',
    template: `
        <!-- Default switch -->
        <div class="form-check form-switch">
            <input type="checkbox" class="form-check-input" id="flexSwitchCheckDefault" [(ngModel)]="checked" (change)="getCheckedFlag()" />
            <label
                *ngIf="checked === false || checked === undefined"
                class="form-check-label"
                for="flexSwitchCheckDefault"
                jhiTranslate="artemisApp.exercise.assessmentInstructions"
            >
                Assessment Instructions</label
            >
            <label *ngIf="checked === true" class="form-check-label" for="flexSwitchCheckDefault" jhiTranslate="artemisApp.exercise.structuredAssessmentInstructions">
                Structured Assessment Instructions</label
            >
        </div>
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

import { Component, OnInit, OnChanges } from '@angular/core';

@Component({
    selector: 'jhi-slide-toggle',
    template: `
        <!-- Default switch -->
        <div class="custom-control custom-switch">
            <input type="checkbox" class="custom-control-input" id="customSwitches" [(ngModel)]="checked" (change)="toggleAll(checked)" />
            <label class="custom-control-label" for="customSwitches"></label>
        </div>
        <label *ngIf="checked === false" jhiTranslate="artemisApp.exercise.gradingInstructions"> Grading Instructions</label>
        <label *ngIf="checked === true" jhiTranslate="artemisApp.exercise.structuredGradingInstructions"> Structured Grading Instructions</label>
    `,
})
export class SlideToggleComponent implements OnInit {
    checked: boolean;

    constructor() {}

    ngOnInit() {
        this.checked = false;
    }
    toggleAll(checked: boolean) {}
}

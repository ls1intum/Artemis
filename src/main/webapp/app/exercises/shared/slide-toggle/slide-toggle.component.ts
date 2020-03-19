import { Component, OnInit, Output, EventEmitter } from '@angular/core';

@Component({
    selector: 'jhi-slide-toggle',
    template: `
        <!-- Default switch -->
        <div class="custom-control custom-switch" *jhiHasAnyAuthority="'ROLE_ADMIN'">
            <input type="checkbox" class="custom-control-input" id="customSwitches" [(ngModel)]="checked" (change)="getCheckedFlag()" />
            <label class="custom-control-label" for="customSwitches"></label>
        </div>
        <label *ngIf="checked === false" jhiTranslate="artemisApp.exercise.gradingInstructions"> Grading Instructions</label>
        <label *ngIf="checked === true" jhiTranslate="artemisApp.exercise.structuredGradingInstructions"> Structured Grading Instructions</label>
    `,
})
export class SlideToggleComponent implements OnInit {
    @Output() checkedEmitter = new EventEmitter<boolean>();
    checked: boolean;

    constructor() {}

    ngOnInit(): void {
        this.checked = false;
    }

    getCheckedFlag() {
        this.checkedEmitter.emit(this.checked);
    }
}

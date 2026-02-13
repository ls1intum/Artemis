import { Component, computed, input } from '@angular/core';

@Component({
    selector: 'jhi-stepper',
    standalone: true,
    template: `
        <div class="stepper" role="img" [attr.aria-label]="'Step ' + currentStep() + ' of ' + totalSteps()">
            @for (step of steps(); track step) {
                <div class="step-indicator" [class.active]="step === currentStep()" [class.inactive]="step !== currentStep()"></div>
            }
        </div>
    `,
    styleUrls: ['./stepper.component.scss'],
})
export class StepperComponent {
    currentStep = input.required<number>();
    totalSteps = input.required<number>();

    steps = computed(() => {
        const total = this.totalSteps();
        return Array.from({ length: total }, (_, i) => i + 1);
    });
}

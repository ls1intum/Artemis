import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-stepper',
    standalone: true,
    template: `
        <div class="stepper" role="img" [attr.aria-label]="ariaLabel()">
            @for (step of steps(); track step) {
                <div class="step-indicator" [class.active]="step === currentStep()" [class.inactive]="step !== currentStep()"></div>
            }
        </div>
    `,
    styleUrls: ['./stepper.component.scss'],
})
export class StepperComponent {
    private readonly translateService = inject(TranslateService);

    currentStep = input.required<number>();
    totalSteps = input.required<number>();

    steps = computed(() => {
        const total = this.totalSteps();
        return Array.from({ length: total }, (_, i) => i + 1);
    });

    private readonly langChange = toSignal(this.translateService.stream('artemisApp.iris.onboarding.stepOf'), {
        initialValue: this.translateService.instant('artemisApp.iris.onboarding.stepOf'),
    });

    ariaLabel = computed(() => {
        this.langChange(); // subscribe to language changes to trigger recomputation
        return this.translateService.instant('artemisApp.iris.onboarding.stepOf', { current: this.currentStep(), total: this.totalSteps() });
    });
}

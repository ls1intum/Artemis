import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { StepperComponent } from './stepper.component';

describe('StepperComponent', () => {
    setupTestBed({ zoneless: true });

    let component: StepperComponent;
    let fixture: ComponentFixture<StepperComponent>;
    let componentRef: ComponentRef<StepperComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [StepperComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(StepperComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
        componentRef.setInput('currentStep', 1);
        componentRef.setInput('totalSteps', 3);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should generate correct number of steps', () => {
        expect(component.steps()).toEqual([1, 2, 3]);
    });

    it('should update steps when totalSteps changes', () => {
        componentRef.setInput('totalSteps', 5);
        expect(component.steps()).toEqual([1, 2, 3, 4, 5]);
    });

    it('should render step indicators with correct active/inactive classes', () => {
        const indicators = fixture.nativeElement.querySelectorAll('.step-indicator');
        expect(indicators).toHaveLength(3);
        expect(indicators[0].classList.contains('active')).toBeTruthy();
        expect(indicators[0].classList.contains('inactive')).toBeFalsy();
        expect(indicators[1].classList.contains('inactive')).toBeTruthy();
        expect(indicators[2].classList.contains('inactive')).toBeTruthy();
    });

    it('should update active indicator when currentStep changes', async () => {
        componentRef.setInput('currentStep', 2);
        fixture.detectChanges();
        await fixture.whenStable();

        const indicators = fixture.nativeElement.querySelectorAll('.step-indicator');
        expect(indicators[0].classList.contains('inactive')).toBeTruthy();
        expect(indicators[1].classList.contains('active')).toBeTruthy();
        expect(indicators[2].classList.contains('inactive')).toBeTruthy();
    });
});

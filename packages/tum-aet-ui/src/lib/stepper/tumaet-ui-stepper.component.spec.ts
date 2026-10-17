import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TumAetUiStepComponent } from './tumaet-ui-step.component';
import { TumAetUiStepperComponent } from './tumaet-ui-stepper.component';

@Component({
    template: `
        <tumaet-ui-stepper [orientation]="orientation" ariaLabel="Generation progress">
            <tumaet-ui-step state="complete" label="Prepare workspace" stateLabel="Complete" />
            <tumaet-ui-step state="current" label="Design" stateLabel="Running" />
            <tumaet-ui-step state="skipped" label="Build and test" stateLabel="Skipped" />
            <tumaet-ui-step label="Save" stateLabel="Pending" />
        </tumaet-ui-stepper>
    `,
    imports: [TumAetUiStepperComponent, TumAetUiStepComponent],
})
class StepperHostComponent {
    orientation: 'vertical' | 'horizontal' = 'vertical';
}

describe('TumAetUiStepperComponent', () => {
    let fixture: ComponentFixture<StepperHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StepperHostComponent, FontAwesomeTestingModule],
        }).compileComponents();
        fixture = TestBed.createComponent(StepperHostComponent);
        fixture.detectChanges();
    });

    function list(): HTMLElement {
        return fixture.debugElement.query(By.css('ol')).nativeElement;
    }

    function steps(): HTMLElement[] {
        return fixture.debugElement.queryAll(By.css('tumaet-ui-step')).map((step) => step.nativeElement);
    }

    it('renders the steps inside a labelled list', () => {
        expect(list().getAttribute('aria-label')).toBe('Generation progress');
        expect(steps()).toHaveLength(4);
    });

    it('keeps an explicit list role, which the steps depend on', () => {
        expect(list().getAttribute('role')).toBe('list');
    });

    it('exposes every step as a list item of that list', () => {
        for (const step of steps()) {
            expect(step.getAttribute('role')).toBe('listitem');
            expect(step.parentElement).toBe(list());
        }
    });

    it('marks exactly one step as the current one', () => {
        const current = steps().filter((step) => step.getAttribute('aria-current') === 'step');
        expect(current).toHaveLength(1);
        expect(current[0].textContent).toContain('Design');
    });

    it('marks only the steps that have not run as disabled', () => {
        const disabled = steps()
            .filter((step) => step.getAttribute('aria-disabled') === 'true')
            .map((step) => step.textContent?.trim());
        expect(disabled).toHaveLength(2);
        expect(disabled[0]).toContain('Build and test');
        expect(disabled[1]).toContain('Save');
    });

    it('names each step by its label and its state word', () => {
        expect(steps()[1].textContent?.replace(/\s+/g, ' ').trim()).toBe('Design Running');
    });

    it('keeps the list semantics when laid out horizontally', () => {
        fixture.componentInstance.orientation = 'horizontal';
        fixture.detectChanges();
        expect(list().tagName.toLowerCase()).toBe('ol');
        for (const step of steps()) {
            expect(step.getAttribute('role')).toBe('listitem');
        }
    });
});

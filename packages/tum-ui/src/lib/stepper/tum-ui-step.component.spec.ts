import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { faRotate } from '@fortawesome/free-solid-svg-icons';
import { TumUiStepComponent, TumUiStepState } from './tum-ui-step.component';

describe('TumUiStepComponent', () => {
    let fixture: ComponentFixture<TumUiStepComponent>;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiStepComponent, FontAwesomeTestingModule],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiStepComponent);
        host = fixture.nativeElement as HTMLElement;
        fixture.componentRef.setInput('label', 'Build and test');
        fixture.detectChanges();
    });

    function withState(state: TumUiStepState): void {
        fixture.componentRef.setInput('state', state);
        fixture.detectChanges();
    }

    function renderedIcon(): string | undefined {
        return fixture.debugElement.query(By.css('fa-icon svg'))?.nativeElement.getAttribute('data-icon') ?? undefined;
    }

    function runningIndicator(): HTMLElement | undefined {
        return fixture.debugElement.query(By.css('.tum-ui-step-running-indicator'))?.nativeElement;
    }

    it('is a list item that is neither current nor disabled by its label alone', () => {
        expect(host.getAttribute('role')).toBe('listitem');
        expect(host.textContent).toContain('Build and test');
    });

    it('marks pending and skipped steps as disabled, and the running ones as not', () => {
        for (const state of ['pending', 'skipped'] as const) {
            withState(state);
            expect(host.getAttribute('aria-disabled')).toBe('true');
        }
        for (const state of ['current', 'complete', 'failed'] as const) {
            withState(state);
            expect(host.getAttribute('aria-disabled')).toBeNull();
        }
    });

    it('marks only the current step with aria-current', () => {
        withState('current');
        expect(host.getAttribute('aria-current')).toBe('step');
        withState('complete');
        expect(host.getAttribute('aria-current')).toBeNull();
    });

    it('renders a distinct marker icon for each finished state', () => {
        withState('complete');
        expect(renderedIcon()).toBe('check');
        withState('failed');
        expect(renderedIcon()).toBe('xmark');
        withState('skipped');
        expect(renderedIcon()).toBe('minus');
    });

    it('renders a hollow marker for a pending step and a running indicator for the current one', () => {
        withState('pending');
        expect(renderedIcon()).toBeUndefined();
        expect(runningIndicator()).toBeUndefined();

        withState('current');
        expect(renderedIcon()).toBeUndefined();
        expect(runningIndicator()).toBeDefined();
    });

    it('lets a caller override the marker icon, including on the current step', () => {
        withState('current');
        fixture.componentRef.setInput('icon', faRotate);
        fixture.detectChanges();
        expect(renderedIcon()).toBe('rotate');
        expect(runningIndicator()).toBeUndefined();
    });

    it('adds the state word to the accessible name only when one is supplied', () => {
        withState('failed');
        expect(host.textContent?.replace(/\s+/g, ' ').trim()).toBe('Build and test');

        fixture.componentRef.setInput('stateLabel', 'Failed');
        fixture.detectChanges();
        expect(host.textContent?.replace(/\s+/g, ' ').trim()).toBe('Build and test Failed');
    });
});

import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { faRotate } from '@fortawesome/free-solid-svg-icons';
import { TUM_UI_TRANSLATOR, TumUiTranslator } from '../i18n/tum-ui-translations';
import { TumUiStepComponent, TumUiStepState } from './tum-ui-step.component';
import { TumUiStepperComponent, TumUiStepperOrientation } from './tum-ui-stepper.component';

@Component({
    template: `
        <tum-ui-stepper [orientation]="orientation()">
            <tum-ui-step state="complete" label="Prepare workspace" />
            <tum-ui-step state="current" label="Design" />
        </tum-ui-stepper>
    `,
    imports: [TumUiStepComponent, TumUiStepperComponent],
})
class TwoStepsHostComponent {
    readonly orientation = signal<TumUiStepperOrientation>('vertical');
}

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

    function text(): string {
        return host.textContent?.replace(/\s+/g, ' ').trim() ?? '';
    }

    function renderedIcon(): string | undefined {
        return fixture.debugElement.query(By.css('fa-icon svg'))?.nativeElement.getAttribute('data-icon') ?? undefined;
    }

    function runningIndicator(): HTMLElement | undefined {
        return fixture.debugElement.query(By.css('.tum-ui-step-running-indicator'))?.nativeElement;
    }

    it('is a list item named by its label', () => {
        expect(host.getAttribute('role')).toBe('listitem');
        expect(text()).toContain('Build and test');
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
        expect(renderedIcon()).toBe('forward-step');
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

    it('names the state with the package word when the caller supplies none', () => {
        withState('pending');
        expect(text()).toBe('Build and test Not started');
        withState('current');
        expect(text()).toBe('Build and test In progress');
        withState('complete');
        expect(text()).toBe('Build and test Done');
        withState('failed');
        expect(text()).toBe('Build and test Failed');
        withState('skipped');
        expect(text()).toBe('Build and test Skipped');
    });

    it('prefers the caller state word over the package one', () => {
        withState('current');
        fixture.componentRef.setInput('stateLabel', 'Drafting');
        fixture.detectChanges();
        expect(text()).toBe('Build and test Drafting');
    });

    it('keeps the state word out of the visible line', () => {
        const label = fixture.debugElement.query(By.css('.tum-ui-step-label')).nativeElement as HTMLElement;
        const hidden = label.querySelector('.tum\\:sr-only') as HTMLElement;
        expect(hidden.textContent?.trim()).toBe('Not started');
        expect(label.getAttribute('aria-hidden')).toBeNull();
    });

    it('keeps the marker out of the accessibility tree', () => {
        const track = fixture.debugElement.query(By.css('.tum-ui-step-marker-track')).nativeElement as HTMLElement;
        expect(track.getAttribute('aria-hidden')).toBe('true');
    });
});

describe('TumUiStepComponent state word translation', () => {
    it('resolves the state word through the host translator and follows its catalog', async () => {
        const revision = signal(0);
        const translator: TumUiTranslator = {
            translationChanges: revision,
            translate: (key) => `${key}#${revision()}`,
        };
        await TestBed.configureTestingModule({
            imports: [TumUiStepComponent, FontAwesomeTestingModule],
            providers: [{ provide: TUM_UI_TRANSLATOR, useValue: translator }],
        }).compileComponents();

        const fixture = TestBed.createComponent(TumUiStepComponent);
        fixture.componentRef.setInput('label', 'Design');
        fixture.componentRef.setInput('state', 'failed');
        fixture.detectChanges();
        const host = fixture.nativeElement as HTMLElement;
        expect(host.textContent).toContain('tumUi.step.failed#0');

        revision.set(1);
        fixture.detectChanges();
        expect(host.textContent).toContain('tumUi.step.failed#1');
    });
});

describe('TumUiStepComponent label slot', () => {
    it('renders projected label markup in the same line as the label input', async () => {
        @Component({
            template: `<tum-ui-step state="complete" label="Build"><a tumUiStepLabel href="#log">log</a>detail line</tum-ui-step>`,
            imports: [TumUiStepComponent],
        })
        class LabelSlotHostComponent {}

        await TestBed.configureTestingModule({
            imports: [LabelSlotHostComponent, FontAwesomeTestingModule],
        }).compileComponents();
        const fixture = TestBed.createComponent(LabelSlotHostComponent);
        fixture.detectChanges();

        const label = fixture.debugElement.query(By.css('.tum-ui-step-label')).nativeElement as HTMLElement;
        const detail = fixture.debugElement.query(By.css('.tum-ui-step-detail')).nativeElement as HTMLElement;
        expect(label.querySelector('a')?.textContent).toBe('log');
        expect(label.textContent).toContain('Build');
        expect(detail.textContent?.trim()).toBe('detail line');
    });
});

describe('TumUiStepComponent connector', () => {
    let fixture: ComponentFixture<TwoStepsHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TwoStepsHostComponent, FontAwesomeTestingModule],
        }).compileComponents();
        fixture = TestBed.createComponent(TwoStepsHostComponent);
        fixture.detectChanges();
    });

    function connectors(): HTMLElement[] {
        return fixture.debugElement.queryAll(By.css('.tum-ui-step-connector')).map((element) => element.nativeElement);
    }

    it('gives every step one connector element inside the marker track', () => {
        const tracks = fixture.debugElement.queryAll(By.css('.tum-ui-step-marker-track')).map((element) => element.nativeElement as HTMLElement);
        expect(connectors()).toHaveLength(2);
        for (const [index, connector] of connectors().entries()) {
            expect(connector.parentElement).toBe(tracks[index]);
        }
    });

    // jsdom performs no layout: every getBoundingClientRect() is 0×0 at the origin, so the marker and the connector
    // cannot be measured here. The centring contract is asserted as computed style instead — the connector is placed
    // at half the marker track and pulled back by half its own width, so its centre is the marker's centre by
    // construction rather than by arithmetic on spacing tokens. The measured proof runs in a real browser, in the
    // `ConnectorAlignment` play function of the stepper story.
    it('centres the connector on the marker by construction, in both orientations', () => {
        const connector = connectors()[0];
        const vertical = getComputedStyle(connector);
        expect(vertical.insetInlineStart).toBe('50%');
        expect(vertical.transform).toBe('translateX(-50%)');

        fixture.componentInstance.orientation.set('horizontal');
        fixture.detectChanges();
        const horizontal = getComputedStyle(connector);
        expect(horizontal.insetBlockStart).toBe('50%');
        expect(horizontal.transform).toBe('translateY(-50%)');
    });

    it('draws no connector out of the step that ends the ladder', () => {
        expect(getComputedStyle(connectors()[0]).display).toBe('block');
        expect(getComputedStyle(connectors()[1]).display).toBe('none');
    });
});

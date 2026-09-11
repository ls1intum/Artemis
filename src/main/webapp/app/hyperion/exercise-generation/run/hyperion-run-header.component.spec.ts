import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HyperionRunHeaderComponent } from './hyperion-run-header.component';

describe('HyperionRunHeaderComponent', () => {
    let fixture: ComponentFixture<HyperionRunHeaderComponent>;
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HyperionRunHeaderComponent],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(HyperionRunHeaderComponent);
        fixture.componentRef.setInput('statusState', 'running');
        fixture.componentRef.setInput('statusLabelKey', 'Running');
        fixture.componentRef.setInput('editorLink', ['/editor']);
    });

    it('withholds the editor link until the run ends', () => {
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('a[href="/editor"]')).toBeNull();
        fixture.componentRef.setInput('terminal', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('a[href="/editor"]')).not.toBeNull();
    });

    it('identifies adaptation instead of describing it as a new exercise generation', () => {
        fixture.componentRef.setInput('adapting', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-intent"]').textContent).toContain('adaptationTitle');
    });

    it('emits startRequested from both the start and the run-again buttons while nothing blocks a run', () => {
        const started = vi.fn();
        fixture.componentInstance.startRequested.subscribe(started);
        fixture.componentRef.setInput('startAvailable', true);
        fixture.componentRef.setInput('runAgainAvailable', true);
        fixture.detectChanges();

        const start = button('hyperion-run-start');
        const runAgain = button('hyperion-run-run-again');
        expect(start.getAttribute('aria-disabled')).toBeNull();
        expect(runAgain.getAttribute('aria-disabled')).toBeNull();
        start.click();
        runAgain.click();

        expect(started).toHaveBeenCalledTimes(2);
    });

    it('keeps the start buttons visible but soft-disabled with the reason while a run is blocked', () => {
        const started = vi.fn();
        fixture.componentInstance.startRequested.subscribe(started);
        fixture.componentRef.setInput('startAvailable', true);
        fixture.componentRef.setInput('runAgainAvailable', true);
        fixture.componentRef.setInput('startBlockedReason', 'artemisApp.hyperion.generation.blocker.released');
        fixture.detectChanges();

        const start = button('hyperion-run-start');
        const runAgain = button('hyperion-run-run-again');
        expect(fixture.componentInstance['startBlockedReasonText']()).toBe('artemisApp.hyperion.generation.blocker.released');
        expect(start.getAttribute('aria-disabled')).toBe('true');
        expect(start.hasAttribute('disabled')).toBe(false);
        expect(runAgain.getAttribute('aria-disabled')).toBe('true');
        start.click();
        runAgain.click();

        expect(started).not.toHaveBeenCalled();
    });

    function button(testId: string): HTMLButtonElement {
        return fixture.nativeElement.querySelector(`[data-testid="${testId}"] button`);
    }
});

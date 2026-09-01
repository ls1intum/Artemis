import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumUiStatusDotComponent, TumUiStatusDotState } from './tum-ui-status-dot.component';

describe('TumUiStatusDotComponent', () => {
    let fixture: ComponentFixture<TumUiStatusDotComponent>;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiStatusDotComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiStatusDotComponent);
        host = fixture.nativeElement as HTMLElement;
        fixture.componentRef.setInput('state', 'running');
        fixture.componentRef.setInput('label', 'Running');
        fixture.detectChanges();
    });

    function withState(state: TumUiStatusDotState): void {
        fixture.componentRef.setInput('state', state);
        fixture.detectChanges();
    }

    function dot(): HTMLElement {
        return fixture.debugElement.query(By.css('.tum-ui-status-dot-indicator')).nativeElement;
    }

    it('is named by its state word and keeps the dot out of the accessibility tree', () => {
        expect(host.textContent?.trim()).toBe('Running');
        expect(dot().getAttribute('aria-hidden')).toBe('true');
    });

    it('keeps the accessible name when the label is hidden visually', () => {
        fixture.componentRef.setInput('showLabel', false);
        fixture.detectChanges();
        const label = fixture.debugElement.query(By.css('.tum-ui-status-dot-label')).nativeElement as HTMLElement;
        expect(label.textContent?.trim()).toBe('Running');
        expect(label.classList).toContain('tum:sr-only');
        expect(host.getAttribute('aria-hidden')).toBeNull();
    });

    it('stays plain markup unless the caller asks for a live region', () => {
        expect(host.getAttribute('role')).toBeNull();
        fixture.componentRef.setInput('live', true);
        fixture.detectChanges();
        expect(host.getAttribute('role')).toBe('status');
    });

    it('reports every state it supports for styling and inspection', () => {
        for (const state of ['queued', 'running', 'success', 'warning', 'error', 'neutral', 'unknown'] as const) {
            withState(state);
            expect(host.getAttribute('data-state')).toBe(state);
        }
    });

    // The dot's shape and its pulse are pure CSS driven by `data-state`, and jsdom evaluates neither `@media
    // (prefers-reduced-motion)` nor `var()` substitution. The visible contract — `neutral` filled, `queued` a ring,
    // `unknown` a dashed ring, and only `queued` and `running` animating — is asserted in a real browser by the
    // `StateShapes` play function of the status dot story.
    it('keeps one indicator element whatever the state is', () => {
        for (const state of ['queued', 'running', 'success', 'warning', 'error', 'neutral', 'unknown'] as const) {
            withState(state);
            expect(fixture.debugElement.queryAll(By.css('.tum-ui-status-dot-indicator'))).toHaveLength(1);
            expect(dot().getAttribute('aria-hidden')).toBe('true');
        }
    });
});

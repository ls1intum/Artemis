import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { Component } from '@angular/core';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TumUiPanelComponent } from 'app/shared-ui/tum-ui/panel/tum-ui-panel.component';

describe('TumUiPanelComponent', () => {
    let fixture: ComponentFixture<TumUiPanelComponent>;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiPanelComponent, FontAwesomeTestingModule],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiPanelComponent);
        host = fixture.nativeElement as HTMLElement;
        fixture.componentRef.setInput('header', 'Configuration');
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function toggler(): HTMLButtonElement | null {
        const el = fixture.debugElement.query(By.css('.tum-ui-panel-toggler'));
        return el ? (el.nativeElement as HTMLButtonElement) : null;
    }

    it('renders the panel surface and header title', () => {
        expect(host.className).toContain('tum-ui-panel');
        expect(host.className).toContain('border-surface');
        expect(fixture.debugElement.query(By.css('.tum-ui-panel-title')).nativeElement.textContent.trim()).toBe('Configuration');
    });

    it('is not toggleable by default: no toggle button and no collapsed marker', () => {
        expect(toggler()).toBeNull();
        expect(host.getAttribute('data-collapsed')).toBe('false');
    });

    it('renders a toggle button with expanded/controls wiring when toggleable', () => {
        fixture.componentRef.setInput('toggleable', true);
        fixture.detectChanges();
        const button = toggler()!;
        expect(button).not.toBeNull();
        expect(button.getAttribute('aria-expanded')).toBe('true');
        const controls = button.getAttribute('aria-controls');
        expect(controls).toBeTruthy();
        expect(fixture.debugElement.query(By.css('.tum-ui-panel-content-container')).nativeElement.id).toBe(controls);
        expect(button.getAttribute('aria-label')).toBe('Configuration');
    });

    it('honours an initial collapsed input and flips aria-expanded', () => {
        fixture.componentRef.setInput('toggleable', true);
        fixture.componentRef.setInput('collapsed', true);
        fixture.detectChanges();
        expect(host.getAttribute('data-collapsed')).toBe('true');
        expect(toggler()!.getAttribute('aria-expanded')).toBe('false');
    });

    it('toggles collapsed on click and emits the two-way change', () => {
        fixture.componentRef.setInput('toggleable', true);
        fixture.detectChanges();
        const changes: boolean[] = [];
        fixture.componentInstance.collapsed.subscribe((value) => changes.push(value));

        toggler()!.click();
        fixture.detectChanges();
        expect(fixture.componentInstance.collapsed()).toBe(true);
        expect(host.getAttribute('data-collapsed')).toBe('true');
        expect(toggler()!.getAttribute('aria-expanded')).toBe('false');

        toggler()!.click();
        fixture.detectChanges();
        expect(fixture.componentInstance.collapsed()).toBe(false);
        expect(changes).toEqual([true, false]);
    });
});

@Component({
    template: `<tum-ui-panel header="Config"><pre class="projected">body</pre></tum-ui-panel>`,
    imports: [TumUiPanelComponent],
})
class PanelHostComponent {}

describe('TumUiPanelComponent (projection)', () => {
    it('projects body content into the panel content region', async () => {
        await TestBed.configureTestingModule({
            imports: [PanelHostComponent, FontAwesomeTestingModule],
        }).compileComponents();
        const fixture = TestBed.createComponent(PanelHostComponent);
        fixture.detectChanges();
        const projected = fixture.debugElement.query(By.css('.tum-ui-panel-content .projected'));
        expect(projected).not.toBeNull();
        expect(projected.nativeElement.textContent.trim()).toBe('body');
    });
});

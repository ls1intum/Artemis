import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Component } from '@angular/core';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TumUiPanelComponent } from './tum-ui-panel.component';

describe('TumUiPanelComponent', () => {
    let fixture: ComponentFixture<TumUiPanelComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiPanelComponent, FontAwesomeTestingModule],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiPanelComponent);
        fixture.componentRef.setInput('header', 'Configuration');
        fixture.detectChanges();
    });

    function toggler(): HTMLButtonElement | null {
        const el = fixture.debugElement.query(By.css('.tum-ui-panel-toggler'));
        return el ? (el.nativeElement as HTMLButtonElement) : null;
    }

    it('renders the header title', () => {
        expect(fixture.debugElement.query(By.css('.tum-ui-panel-title')).nativeElement.textContent.trim()).toBe('Configuration');
    });

    it('is not toggleable by default: no toggle button and no collapsed marker', () => {
        expect(toggler()).toBeNull();
    });

    it('renders a toggle button with expanded/controls wiring when toggleable', () => {
        fixture.componentRef.setInput('toggleable', true);
        fixture.detectChanges();
        const button = toggler()!;
        expect(button).not.toBeNull();
        expect(button.getAttribute('aria-expanded')).toBe('true');
        const controls = button.getAttribute('aria-controls');
        expect(controls).toBeTruthy();
        const content = fixture.debugElement.query(By.css('.tum-ui-panel-content-container')).nativeElement as HTMLElement;
        const title = fixture.debugElement.query(By.css('.tum-ui-panel-title')).nativeElement as HTMLElement;
        expect(content.id).toBe(controls);
        expect(content.getAttribute('role')).toBe('region');
        expect(content.getAttribute('aria-labelledby')).toBe(title.id);
        expect(button.getAttribute('aria-label')).toBe('Configuration');
    });

    it('removes collapsed content from interaction and the accessibility tree', () => {
        fixture.componentRef.setInput('toggleable', true);
        fixture.componentRef.setInput('collapsed', true);
        fixture.detectChanges();
        expect(toggler()!.getAttribute('aria-expanded')).toBe('false');
        const content = fixture.debugElement.query(By.css('.tum-ui-panel-content-container')).nativeElement as HTMLElement;
        expect(content.getAttribute('aria-hidden')).toBe('true');
        expect(content.hasAttribute('inert')).toBe(true);
    });

    it('toggles collapsed on click and emits the two-way change', () => {
        fixture.componentRef.setInput('toggleable', true);
        fixture.detectChanges();
        const changes: boolean[] = [];
        fixture.componentInstance.collapsed.subscribe((value) => changes.push(value));

        toggler()!.click();
        fixture.detectChanges();
        expect(fixture.componentInstance.collapsed()).toBe(true);
        expect(toggler()!.getAttribute('aria-expanded')).toBe('false');

        toggler()!.click();
        fixture.detectChanges();
        expect(fixture.componentInstance.collapsed()).toBe(false);
        expect(changes).toEqual([true, false]);
    });
});

@Component({
    template: `
        <tum-ui-panel header="Config" [toggleable]="true" [collapsed]="true">
            <button type="button" class="projected">Action</button>
        </tum-ui-panel>
    `,
    imports: [TumUiPanelComponent],
})
class PanelHostComponent {}

describe('TumUiPanelComponent (projection)', () => {
    it('keeps projected content under the inert collapsed region', async () => {
        await TestBed.configureTestingModule({
            imports: [PanelHostComponent, FontAwesomeTestingModule],
        }).compileComponents();
        const fixture = TestBed.createComponent(PanelHostComponent);
        fixture.detectChanges();
        const projected = fixture.debugElement.query(By.css('.tum-ui-panel-content .projected'));
        expect(projected).not.toBeNull();
        expect(projected.nativeElement.textContent.trim()).toBe('Action');
        const content = fixture.debugElement.query(By.css('.tum-ui-panel-content-container')).nativeElement as HTMLElement;
        expect(content.contains(projected.nativeElement)).toBe(true);
        expect(content.hasAttribute('inert')).toBe(true);
    });
});

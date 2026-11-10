import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TumAetUiTableLayoutComponent } from './tumaet-ui-table-layout.component';

@Component({
    imports: [TumAetUiTableLayoutComponent],
    template: `
        <tumaet-ui-table-layout [loading]="loading()" [fill]="fill()">
            <div tumAetUiTableToolbar [hidden]="!withToolbar()" data-testid="toolbar-content">toolbar</div>
            <table data-testid="table-content"></table>
            <div tumAetUiTableFooter data-testid="footer-content">footer</div>
        </tumaet-ui-table-layout>
    `,
})
class HostComponent {
    readonly loading = signal(false);
    readonly fill = signal(false);
    readonly withToolbar = signal(true);
}

describe('TumAetUiTableLayoutComponent', () => {
    let fixture: ComponentFixture<HostComponent>;
    let root: HTMLElement;

    const query = (selector: string) => root.querySelector<HTMLElement>(selector);

    beforeEach(() => {
        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
        root = fixture.nativeElement as HTMLElement;
    });

    it('projects toolbar, table and footer into their slots', () => {
        expect(query('[data-testid="table-toolbar"] [data-testid="toolbar-content"]')).not.toBeNull();
        expect(query('[data-testid="table-scroll-body"] [data-testid="table-content"]')).not.toBeNull();
        expect(query('[data-testid="footer-content"]')?.parentElement?.tagName.toLowerCase()).toBe('tumaet-ui-table-layout');
        expect(query('[data-testid="table-scroll-body"] [data-testid="footer-content"]')).toBeNull();
    });

    it('keeps a hidden toolbar node in the slot so the frame can collapse it', () => {
        fixture.componentInstance.withToolbar.set(false);
        fixture.detectChanges();

        expect(query('[data-testid="table-toolbar"] [data-testid="toolbar-content"]')?.hidden).toBe(true);
    });

    it('marks the frame busy and shows the overlay only while loading', () => {
        const layout = query('tumaet-ui-table-layout')!;
        expect(layout.getAttribute('aria-busy')).toBeNull();
        expect(query('tumaet-ui-progress-spinner')).toBeNull();

        fixture.componentInstance.loading.set(true);
        fixture.detectChanges();

        expect(layout.getAttribute('aria-busy')).toBe('true');
        expect(query('tumaet-ui-progress-spinner')).not.toBeNull();
    });

    it('takes the parent height only when asked to fill', () => {
        const layout = query('tumaet-ui-table-layout')!;
        expect(layout.classList.contains('tumaet-ui-table-layout-fill')).toBe(false);

        fixture.componentInstance.fill.set(true);
        fixture.detectChanges();

        expect(layout.classList.contains('tumaet-ui-table-layout-fill')).toBe(true);
    });
    it('is bounded by a block parent with a fixed height when filling, instead of growing with its rows', () => {
        fixture.componentInstance.fill.set(true);
        fixture.detectChanges();

        const layout = query('tumaet-ui-table-layout')!;
        const style = getComputedStyle(layout);

        // Not a flex item here (the parent is a plain block), so `flex` has no effect: the frame needs its own height.
        expect(style.height).toBe('100%');
        expect(style.maxHeight).toBe('none');
        expect(style.overflow).toBe('hidden');
    });

    it('caps its height to the free viewport height, never below a usable minimum, when it does not fill', () => {
        const style = getComputedStyle(query('tumaet-ui-table-layout')!);

        expect(style.maxHeight).toContain('14rem');
        expect(style.maxHeight).toContain('--table-layout-offset');
    });
});

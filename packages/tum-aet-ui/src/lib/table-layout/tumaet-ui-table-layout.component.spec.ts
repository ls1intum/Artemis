import { Component, signal } from '@angular/core';
import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TumAetUiTableLayoutComponent } from './tumaet-ui-table-layout.component';

@Component({
    imports: [TumAetUiTableLayoutComponent],
    template: `
        <tumaet-ui-table-layout [loading]="loading()" [fill]="fill()" [empty]="empty()" [emptyTitle]="emptyTitle()" [emptyDescription]="emptyDescription()">
            <div tumAetUiTableToolbar [hidden]="!withToolbar()" data-testid="toolbar-content">toolbar</div>
            <table data-testid="table-content"></table>
            <div tumAetUiTableEmptyActions data-testid="empty-action">Create</div>
            <div tumAetUiTableFooter data-testid="footer-content">footer</div>
        </tumaet-ui-table-layout>
    `,
})
class HostComponent {
    readonly loading = signal(false);
    readonly fill = signal(false);
    readonly withToolbar = signal(true);
    readonly empty = signal(false);
    readonly emptyTitle = signal('tumAetUi.table.empty');
    readonly emptyDescription = signal<string | undefined>(undefined);
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
    describe('available height measurement', () => {
        @Component({
            imports: [TumAetUiTableLayoutComponent],
            template: `
                <div class="scroller" style="overflow-y: auto">
                    <tumaet-ui-table-layout [fill]="fill()"><table></table></tumaet-ui-table-layout>
                </div>
            `,
        })
        class ScrollHostComponent {
            readonly fill = signal(false);
        }

        let resizeCallbacks: (() => void)[];

        beforeEach(() => {
            resizeCallbacks = [];
            vi.stubGlobal(
                'ResizeObserver',
                class {
                    constructor(callback: () => void) {
                        resizeCallbacks.push(callback);
                    }
                    observe = vi.fn();
                    disconnect = vi.fn();
                },
            );
            vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(600);
            vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
                const top = this.tagName.toLowerCase() === 'tumaet-ui-table-layout' ? 100 : 0;
                return { top, bottom: top, left: 0, right: 0, width: 0, height: 0, x: 0, y: top, toJSON: () => ({}) };
            });
        });

        afterEach(() => {
            vi.unstubAllGlobals();
            vi.restoreAllMocks();
        });

        function create(): { layout: HTMLElement; host: ComponentFixture<ScrollHostComponent> } {
            const host = TestBed.createComponent(ScrollHostComponent);
            host.detectChanges();
            return { layout: host.nativeElement.querySelector('tumaet-ui-table-layout') as HTMLElement, host };
        }

        it('caps the frame at the space left below it in its scroll container, whatever sits above it', () => {
            const { layout } = create();

            // 600px container - 100px of content above the frame - 12px gap.
            expect(layout.style.maxHeight).toBe('488px');
        });

        it('keeps a usable minimum when little space is left', () => {
            vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(250);

            expect(create().layout.style.maxHeight).toBe('320px');
        });

        it('follows resizes of the container', () => {
            const { layout, host } = create();
            vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(900);

            resizeCallbacks.forEach((callback) => callback());
            host.detectChanges();

            expect(layout.style.maxHeight).toBe('788px');
        });

        it('does not measure when the frame fills its parent', () => {
            const { layout, host } = create();

            host.componentInstance.fill.set(true);
            host.detectChanges();

            expect(layout.style.maxHeight).toBe('');
        });
    });
    it('keeps a minimum body height so an empty table does not collapse onto its footer', () => {
        const body = getComputedStyle(query('[data-testid="table-scroll-body"]')!);

        expect(body.minHeight).toBe('112px');
        expect(body.overflow).toBe('auto');
    });
    describe('empty state', () => {
        const empty = () => query('[data-testid="table-scroll-body"] [data-testid="table-empty"]');

        it('shows nothing while the table has rows', () => {
            expect(empty()).toBeNull();
        });

        it('shows the default heading under the table when empty', () => {
            fixture.componentInstance.empty.set(true);
            fixture.detectChanges();

            expect(empty()?.textContent).toContain('No entries yet');
            expect(empty()?.previousElementSibling?.getAttribute('data-testid')).toBe('table-content');
        });

        it('shows the page wording and projects the actions', () => {
            fixture.componentInstance.empty.set(true);
            fixture.componentInstance.emptyTitle.set('tumAetUi.table.noResults');
            fixture.componentInstance.emptyDescription.set('tumAetUi.table.searchPlaceholder');
            fixture.detectChanges();

            expect(empty()?.textContent).toContain('No results found');
            expect(empty()?.textContent).toContain('Search');
            expect(empty()?.querySelector('[data-testid="empty-action"]')?.textContent).toBe('Create');
        });
    });
});

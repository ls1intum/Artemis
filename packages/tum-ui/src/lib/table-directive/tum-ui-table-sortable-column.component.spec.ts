import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TumUiTableDirective, TumUiTableSortEvent } from './tum-ui-table.directive';
import { TumUiTableSortableColumnComponent } from './tum-ui-table-sortable-column.component';

@Component({
    template: `
        <table tumUiTable [sortField]="sortField()" [sortOrder]="sortOrder()" (sortChange)="events.push($event)">
            <thead>
                <tr>
                    <th tumUiSortableColumn="login" class="w-2/5" data-testid="login-col">Login</th>
                    <th tumUiSortableColumn="email" data-testid="email-col">Email</th>
                    <th tumUiSortableColumn="frozen" [disabled]="true" data-testid="frozen-col">Frozen</th>
                </tr>
            </thead>
        </table>
    `,
    imports: [TumUiTableDirective, TumUiTableSortableColumnComponent],
})
class HostComponent {
    readonly sortField = signal<string | undefined>('login');
    readonly sortOrder = signal(1);
    events: TumUiTableSortEvent[] = [];
}

describe('TumUiTableSortableColumnComponent', () => {
    let fixture: ComponentFixture<HostComponent>;
    let host: HostComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();
    });

    function th(testid: string): HTMLElement {
        return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testid}"]`)!;
    }

    it('appends the sort-icon SVG after the projected header text', () => {
        const login = th('login-col');
        expect(login.textContent).toContain('Login');
        const svg = login.querySelector('svg.tum-ui-sort-icon');
        expect(svg).not.toBeNull();
    });

    it('reflects the controlled sort state via aria-sort and the icon paths', () => {
        expect(th('login-col').getAttribute('aria-sort')).toBe('ascending');
        expect(th('login-col').querySelectorAll('svg.tum-ui-sort-icon path').length).toBe(1);
        expect(th('email-col').getAttribute('aria-sort')).toBe('none');
        expect(th('email-col').querySelectorAll('svg.tum-ui-sort-icon path').length).toBe(4);

        host.sortOrder.set(-1);
        fixture.detectChanges();
        expect(th('login-col').getAttribute('aria-sort')).toBe('descending');
        expect(th('login-col').querySelectorAll('svg.tum-ui-sort-icon path').length).toBe(1);
    });

    it('preserves the consumer static class alongside its own host classes (e.g. width utilities)', () => {
        const login = th('login-col');
        expect(login.classList.contains('w-2/5')).toBe(true);
        expect(login.classList.contains('cursor-pointer')).toBe(true);
    });

    it('is focusable, except when disabled', () => {
        expect(th('login-col').getAttribute('tabindex')).toBe('0');
        expect(th('frozen-col').getAttribute('tabindex')).toBeNull();
    });

    it('toggles the active field on click (emits order -1)', () => {
        th('login-col').click();
        expect(host.events.at(-1)).toEqual({ field: 'login', order: -1 });
    });

    it('sorts a different field ascending on click (emits order 1)', () => {
        th('email-col').click();
        expect(host.events.at(-1)).toEqual({ field: 'email', order: 1 });
    });

    it('sorts on Enter / Space keyboard activation', () => {
        th('email-col').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        expect(host.events.at(-1)).toEqual({ field: 'email', order: 1 });

        th('login-col').dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));
        expect(host.events.at(-1)).toEqual({ field: 'login', order: -1 });
    });

    it('does not sort when the column is disabled', () => {
        th('frozen-col').click();
        th('frozen-col').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        expect(host.events).toHaveLength(0);
        expect(th('frozen-col').getAttribute('aria-sort')).toBe('none');
    });
});

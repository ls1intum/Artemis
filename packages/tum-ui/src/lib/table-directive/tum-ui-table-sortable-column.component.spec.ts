import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TumUiTableDirective, TumUiTableSortEvent } from './tum-ui-table.directive';
import { TumUiTableSortableColumnComponent } from './tum-ui-table-sortable-column.component';

@Component({
    template: `
        <table tumUiTable [sortField]="sortField()" [sortOrder]="sortOrder()" (sortChange)="events.push($event)">
            <thead>
                <tr>
                    <th tumUiSortableColumn="login">Login</th>
                    <th tumUiSortableColumn="email">Email</th>
                    <th tumUiSortableColumn="frozen" [disabled]="true">Frozen</th>
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

    function th(label: string): HTMLElement {
        return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('th')).find((element) => element.textContent?.includes(label))!;
    }

    function button(label: string): HTMLButtonElement {
        return th(label).querySelector('button')!;
    }

    it('renders a sort icon after the projected header text', () => {
        const login = th('Login');
        expect(login.textContent).toContain('Login');
        expect(login.querySelector('fa-icon.tum-ui-sort-icon')).not.toBeNull();
    });

    it('reflects the controlled sort state via aria-sort', () => {
        expect(th('Login').getAttribute('aria-sort')).toBe('ascending');
        expect(th('Email').getAttribute('aria-sort')).toBe('none');

        host.sortOrder.set(-1);
        fixture.detectChanges();
        expect(th('Login').getAttribute('aria-sort')).toBe('descending');
    });

    it('uses native buttons for activation and disables them natively', () => {
        expect(th('Login').getAttribute('tabindex')).toBeNull();
        expect(button('Login').type).toBe('button');
        expect(button('Login').disabled).toBe(false);
        expect(button('Frozen').disabled).toBe(true);
    });

    it('toggles the active field on click (emits order -1)', () => {
        button('Login').click();
        expect(host.events.at(-1)).toEqual({ field: 'login', order: -1 });
    });

    it('sorts a different field ascending on click (emits order 1)', () => {
        button('Email').click();
        expect(host.events.at(-1)).toEqual({ field: 'email', order: 1 });
    });

    it('does not sort when the column is disabled', () => {
        button('Frozen').click();
        expect(host.events).toHaveLength(0);
        expect(th('Frozen').getAttribute('aria-sort')).toBe('none');
    });
});

import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumUiTableDirective, TumUiTableSortEvent } from './tum-ui-table.directive';

@Component({
    template: `
        <table tumUiTable [sortField]="sortField()" [sortOrder]="sortOrder()" (sortChange)="events.push($event)">
            <thead>
                <tr>
                    <th>Header</th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td>Cell</td>
                </tr>
            </tbody>
        </table>
    `,
    imports: [TumUiTableDirective],
})
class HostComponent {
    readonly sortField = signal<string | undefined>(undefined);
    readonly sortOrder = signal(1);
    events: TumUiTableSortEvent[] = [];
}

describe('TumUiTableDirective', () => {
    let fixture: ComponentFixture<HostComponent>;
    let host: HostComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();
    });

    function directive(): TumUiTableDirective {
        return fixture.debugElement.query(By.directive(TumUiTableDirective)).injector.get(TumUiTableDirective);
    }

    it('toggles the order when re-sorting the active field', () => {
        host.sortField.set('name');
        host.sortOrder.set(1);
        fixture.detectChanges();

        directive().requestSort('name');
        expect(host.events.at(-1)).toEqual({ field: 'name', order: -1 });

        host.sortOrder.set(-1);
        fixture.detectChanges();
        directive().requestSort('name');
        expect(host.events.at(-1)).toEqual({ field: 'name', order: 1 });
    });

    it('sorts a newly selected field ascending (defaultSortOrder)', () => {
        host.sortField.set('name');
        host.sortOrder.set(-1);
        fixture.detectChanges();

        directive().requestSort('email');
        expect(host.events.at(-1)).toEqual({ field: 'email', order: 1 });
    });
});

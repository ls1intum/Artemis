import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumUiTableDirective, TumUiTableSize, TumUiTableSortEvent } from 'app/shared-ui/tum-ui/table-directive/tum-ui-table.directive';

// Host fields are signals so mutations propagate to the directive's inputs under the zoneless test harness
// (plain-field mutation + fixture.detectChanges() does not refresh child input bindings in zoneless mode).
@Component({
    template: `
        <table
            tumUiTable
            [size]="size()"
            [striped]="striped()"
            [scrollable]="scrollable()"
            [rowHover]="rowHover()"
            class="mt-3"
            [class]="extraClasses()"
            [sortField]="sortField()"
            [sortOrder]="sortOrder()"
            (sortChange)="events.push($event)"
        >
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
    readonly size = signal<TumUiTableSize>('normal');
    readonly striped = signal(false);
    readonly scrollable = signal(false);
    readonly rowHover = signal(false);
    readonly extraClasses = signal('shadow');
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

    function tableClass(): string {
        return (fixture.nativeElement as HTMLElement).querySelector('table')!.className;
    }

    function directive(): TumUiTableDirective {
        return fixture.debugElement.query(By.directive(TumUiTableDirective)).injector.get(TumUiTableDirective);
    }

    it('applies the base + normal-size p-table-matched styling classes', () => {
        const cls = tableClass();
        expect(cls).toContain('tum-ui-table');
        expect(cls).toContain('w-full');
        expect(cls).toContain('border-collapse');
        // Normal density: header + body cell padding 0.75rem 1rem.
        expect(cls).toContain('[&_thead_th]:px-4');
        expect(cls).toContain('[&_thead_th]:py-3');
        expect(cls).toContain('[&_tbody_td]:px-4');
        expect(cls).toContain('[&_tbody_td]:py-3');
        // Header chrome + 1px separators on header and body cells.
        expect(cls).toContain('[&_thead_th]:bg-surface-0');
        expect(cls).toContain('[&_thead_th]:font-semibold');
        expect(cls).toContain('[&_thead_th]:border-b');
        expect(cls).toContain('[&_thead_th]:border-solid');
        expect(cls).toContain('[&_tbody_td]:border-b');
    });

    it('switches to the compact (small) cell padding', () => {
        host.size.set('small');
        fixture.detectChanges();
        const cls = tableClass();
        expect(cls).toContain('[&_thead_th]:px-2');
        expect(cls).toContain('[&_thead_th]:py-1.5');
        expect(cls).toContain('[&_tbody_td]:py-1.5');
        expect(cls).not.toContain('[&_thead_th]:px-4');
    });

    it('adds the zebra-striping classes only when striped', () => {
        expect(tableClass()).not.toContain('[&_tbody_tr:nth-child(odd)]:bg-surface-50');
        host.striped.set(true);
        fixture.detectChanges();
        expect(tableClass()).toContain('[&_tbody_tr:nth-child(odd)]:bg-surface-50');
        expect(tableClass()).toContain('dark:[&_tbody_tr:nth-child(odd)]:bg-surface-950');
    });

    it('adds the sticky-header classes only when scrollable', () => {
        expect(tableClass()).not.toContain('[&_thead_th]:sticky');
        host.scrollable.set(true);
        fixture.detectChanges();
        expect(tableClass()).toContain('[&_thead_th]:sticky');
        expect(tableClass()).toContain('[&_thead_th]:top-0');
    });

    it('adds the row-hover classes only when rowHover', () => {
        expect(tableClass()).not.toContain('[&_tbody_tr:hover]:bg-surface-100');
        host.rowHover.set(true);
        fixture.detectChanges();
        expect(tableClass()).toContain('[&_tbody_tr:hover]:bg-surface-100');
    });

    it('merges both a static and a bound consumer class with its own host classes', () => {
        expect(tableClass()).toContain('mt-3');
        expect(tableClass()).toContain('shadow');
        expect(tableClass()).toContain('tum-ui-table');
    });

    it('keeps its own host classes when a bound consumer class changes', () => {
        host.extraClasses.set('ring-2');
        fixture.detectChanges();
        expect(tableClass()).toContain('ring-2');
        expect(tableClass()).not.toContain('shadow');
        expect(tableClass()).toContain('mt-3');
        expect(tableClass()).toContain('tum-ui-table');
    });

    it('toggles the order when re-sorting the active field (parity with p-table single sort)', () => {
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

import { Component, signal } from '@angular/core';
import { CdkFixedSizeVirtualScroll, CdkVirtualScrollViewport } from '@angular/cdk/scrolling';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumUiTableVirtualScrollComponent } from 'app/shared-ui/tum-ui/table-directive/tum-ui-table-virtual-scroll.component';

interface Row {
    name: string;
}

@Component({
    template: `
        <tum-ui-table-virtual-scroll [items]="items" [itemSize]="40" [rowTemplate]="rowTpl" [striped]="striped()" scrollHeight="20rem" minWidth="30rem" ariaDescribedBy="desc">
            <div class="flex-1" data-testid="header-name">Name</div>
        </tum-ui-table-virtual-scroll>
        <ng-template #rowTpl let-row
            ><span class="row-name">{{ row.name }}</span></ng-template
        >
    `,
    imports: [TumUiTableVirtualScrollComponent],
})
class HostComponent {
    items: Row[] = Array.from({ length: 50 }, (_, i) => ({ name: `logger-${i}` }));
    readonly striped = signal(false);
}

describe('TumUiTableVirtualScrollComponent', () => {
    let fixture: ComponentFixture<HostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
    });

    function el(testid: string): HTMLElement | null {
        return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testid}"]`);
    }

    it('renders the CDK virtual-scroll viewport and the projected header', () => {
        expect(el('tum-ui-vs-table')).not.toBeNull();
        expect(el('tum-ui-vs-viewport')).not.toBeNull();
        expect(el('header-name')?.textContent).toContain('Name');
        expect(el('tum-ui-vs-table')?.getAttribute('aria-describedby')).toBe('desc');
    });

    it('forwards the fixed item size to the CDK fixed-size scroll strategy', () => {
        const fixedSize = fixture.debugElement.query(By.directive(CdkFixedSizeVirtualScroll)).injector.get(CdkFixedSizeVirtualScroll);
        expect(fixedSize.itemSize).toBe(40);
    });

    it('materializes rows from the row template for the visible window', () => {
        const viewport = fixture.debugElement.query(By.directive(CdkVirtualScrollViewport)).componentInstance as CdkVirtualScrollViewport;
        viewport.checkViewportSize();
        fixture.detectChanges();

        const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="tum-ui-vs-row"]');
        // Virtual scroll renders only the visible slice (a small buffer here), never all 50 rows.
        expect(rows.length).toBeGreaterThan(0);
        expect(rows.length).toBeLessThan(50);
        // Rows come from the consumer's rowTemplate and carry the fixed row height.
        expect(rows[0].querySelector('.row-name')?.textContent).toContain('logger-0');
        expect((rows[0] as HTMLElement).style.height).toBe('40px');
    });

    it('applies the p-table-matched header + row styling tokens', () => {
        const component = fixture.debugElement.query(By.directive(TumUiTableVirtualScrollComponent)).componentInstance as TumUiTableVirtualScrollComponent<Row>;
        // Access protected computeds via bracket notation for the assertion.
        const headerClasses = (component as unknown as { headerClasses: () => string }).headerClasses();
        const rowClasses = (component as unknown as { rowClasses: () => string }).rowClasses();
        expect(headerClasses).toContain('font-semibold');
        expect(headerClasses).toContain('bg-surface-0');
        expect(headerClasses).toContain('border-b');
        expect(headerClasses).toContain('px-4');
        expect(rowClasses).toContain('border-b');
        expect(rowClasses).toContain('px-4');
        expect(rowClasses).not.toContain('odd:bg-surface-50');
    });

    it('stripes even-index rows only when striped (index-based, not :nth-child, so it survives CDK recycling)', () => {
        const component = fixture.debugElement.query(By.directive(TumUiTableVirtualScrollComponent)).componentInstance as TumUiTableVirtualScrollComponent<Row>;
        const stripeClass = (i: number) => (component as unknown as { stripeClass: (i: number) => string }).stripeClass(i);
        // Not striped by default → no stripe on any row.
        expect(stripeClass(0)).toBe('');
        fixture.componentInstance.striped.set(true);
        fixture.detectChanges();
        expect(stripeClass(0)).toContain('bg-surface-50');
        expect(stripeClass(0)).toContain('dark:bg-surface-950');
        // Odd data index gets no stripe — keyed on the stable data index, never a DOM `:nth-child` variant.
        expect(stripeClass(1)).toBe('');
    });
});

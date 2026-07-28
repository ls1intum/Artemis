import { Component } from '@angular/core';
import { CdkFixedSizeVirtualScroll, CdkVirtualScrollViewport } from '@angular/cdk/scrolling';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumUiTableVirtualScrollComponent } from './tum-ui-table-virtual-scroll.component';

interface Row {
    name: string;
}

@Component({
    template: `
        <tum-ui-table-virtual-scroll [items]="items" [itemSize]="40" [rowTemplate]="rowTpl" scrollHeight="20rem" minWidth="30rem" ariaDescribedBy="desc">
            <div class="flex-1">Name</div>
        </tum-ui-table-virtual-scroll>
        <ng-template #rowTpl let-row
            ><span class="row-name">{{ row.name }}</span></ng-template
        >
    `,
    imports: [TumUiTableVirtualScrollComponent],
})
class HostComponent {
    items: Row[] = Array.from({ length: 50 }, (_, i) => ({ name: `logger-${i}` }));
}

describe('TumUiTableVirtualScrollComponent', () => {
    let fixture: ComponentFixture<HostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
    });

    it('renders the CDK virtual-scroll viewport and the projected header', () => {
        const table = (fixture.nativeElement as HTMLElement).querySelector('[role="table"]');
        expect(table).not.toBeNull();
        expect(table?.querySelector('[role="rowgroup"]')).not.toBeNull();
        expect(table?.querySelector('[role="row"]')?.textContent).toContain('Name');
        expect(table?.getAttribute('aria-describedby')).toBe('desc');
        expect(table?.getAttribute('aria-rowcount')).toBe('51');
        expect(table?.querySelector('[role="row"]')?.getAttribute('aria-rowindex')).toBe('1');
    });

    it('forwards the fixed item size to the CDK fixed-size scroll strategy', () => {
        const fixedSize = fixture.debugElement.query(By.directive(CdkFixedSizeVirtualScroll)).injector.get(CdkFixedSizeVirtualScroll);
        expect(fixedSize.itemSize).toBe(40);
    });

    it('materializes rows from the row template for the visible window', () => {
        const viewport = fixture.debugElement.query(By.directive(CdkVirtualScrollViewport)).componentInstance as CdkVirtualScrollViewport;
        viewport.checkViewportSize();
        fixture.detectChanges();

        const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('[role="rowgroup"] [role="row"]');
        expect(rows.length).toBeGreaterThan(0);
        expect(rows.length).toBeLessThan(50);
        expect(rows[0].querySelector('.row-name')?.textContent).toContain('logger-0');
        expect((rows[0] as HTMLElement).style.height).toBe('40px');
        expect(rows[0].getAttribute('aria-rowindex')).toBe('2');
    });
});

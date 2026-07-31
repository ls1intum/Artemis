import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TumUiPaginatorComponent } from './tum-ui-paginator.component';

describe('TumUiPaginatorComponent', () => {
    let component: TumUiPaginatorComponent;
    let fixture: ComponentFixture<TumUiPaginatorComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiPaginatorComponent, FontAwesomeTestingModule],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiPaginatorComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => vi.restoreAllMocks());

    function setInputs(total: number, page: number, size = 50): void {
        fixture.componentRef.setInput('totalRecords', total);
        fixture.componentRef.setInput('page', page);
        fixture.componentRef.setInput('pageSize', size);
        fixture.detectChanges();
    }

    function navButton(label: string): HTMLButtonElement {
        return fixture.debugElement.query(By.css(`button[aria-label="${label} page"]`)).nativeElement;
    }

    function pageButtons(): HTMLButtonElement[] {
        return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).filter((button) => /^\d+$/.test(button.textContent?.trim() ?? ''));
    }

    it('disables first/previous on the first page', () => {
        setInputs(130, 0);
        expect(navButton('First').disabled).toBe(true);
        expect(navButton('Previous').disabled).toBe(true);
        expect(navButton('Next').disabled).toBe(false);
        expect(navButton('Last').disabled).toBe(false);
    });

    it('disables next/last on the last page', () => {
        setInputs(130, 2);
        expect(navButton('Next').disabled).toBe(true);
        expect(navButton('Last').disabled).toBe(true);
        expect(navButton('First').disabled).toBe(false);
    });

    it('emits pageChange for next/previous/first/last', () => {
        const spy = vi.spyOn(component.pageChange, 'emit');
        setInputs(130, 1);
        navButton('Next').click();
        expect(spy).toHaveBeenLastCalledWith(2);
        navButton('Previous').click();
        expect(spy).toHaveBeenLastCalledWith(0);
        navButton('Last').click();
        expect(spy).toHaveBeenLastCalledWith(2);
        navButton('First').click();
        expect(spy).toHaveBeenLastCalledWith(0);
    });

    it('emits pageSizeChange from the rows-per-page select', async () => {
        const spy = vi.spyOn(component.pageSizeChange, 'emit');
        setInputs(130, 0, 50);
        await fixture.whenStable();
        fixture.detectChanges();
        const select: HTMLSelectElement = fixture.debugElement.query(By.css('select')).nativeElement;
        expect(select.labels?.[0]?.textContent).toContain('Rows per page');
        expect(select.selectedOptions[0].textContent).toBe('50');
        select.value = Array.from(select.options).find((option) => option.textContent === '20')!.value;
        select.dispatchEvent(new Event('change'));
        expect(spy).toHaveBeenCalledWith(20);
    });

    it('renders the current-page report element', () => {
        setInputs(130, 0);
        const host = fixture.nativeElement as HTMLElement;
        const navigation = host.querySelector('nav');
        const report = host.querySelector('[aria-live="polite"]');
        expect(navigation?.getAttribute('aria-label')).toBe('Pagination');
        expect(report?.textContent).toContain('Showing 1 to 50 of 130');
    });

    it('renders windowed page-number buttons (max 5) and marks the current page', () => {
        setInputs(500, 4, 50);
        const pages = pageButtons();
        expect(pages.length).toBe(5);
        const current = pages.find((button) => button.getAttribute('aria-current') === 'page');
        expect(current?.textContent?.trim()).toBe('5');
    });

    it('emits pageChange when a page-number button is clicked', () => {
        const spy = vi.spyOn(component.pageChange, 'emit');
        setInputs(500, 4, 50);
        pageButtons()[0].click();
        expect(spy).toHaveBeenCalledWith(2);
    });

    it('clamps the display when the page input exceeds the last valid page (no stranded/empty state)', () => {
        setInputs(30, 5, 50);
        expect(navButton('Next').disabled).toBe(true);
        expect(navButton('Last').disabled).toBe(true);
        const pages = pageButtons();
        expect(pages.length).toBe(1);
        expect(pages[0].getAttribute('aria-current')).toBe('page');
    });
});

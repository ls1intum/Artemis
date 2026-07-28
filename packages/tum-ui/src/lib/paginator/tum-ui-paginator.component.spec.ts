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

    function navButton(testid: string): HTMLButtonElement {
        return fixture.debugElement.query(By.css(`[data-testid="${testid}"]`)).nativeElement;
    }

    it('disables first/previous on the first page', () => {
        setInputs(130, 0);
        expect(navButton('paginator-first').disabled).toBe(true);
        expect(navButton('paginator-previous').disabled).toBe(true);
        expect(navButton('paginator-next').disabled).toBe(false);
        expect(navButton('paginator-last').disabled).toBe(false);
    });

    it('disables next/last on the last page', () => {
        setInputs(130, 2);
        expect(navButton('paginator-next').disabled).toBe(true);
        expect(navButton('paginator-last').disabled).toBe(true);
        expect(navButton('paginator-first').disabled).toBe(false);
    });

    it('emits pageChange for next/previous/first/last', () => {
        const spy = vi.spyOn(component.pageChange, 'emit');
        setInputs(130, 1);
        navButton('paginator-next').click();
        expect(spy).toHaveBeenLastCalledWith(2);
        navButton('paginator-previous').click();
        expect(spy).toHaveBeenLastCalledWith(0);
        navButton('paginator-last').click();
        expect(spy).toHaveBeenLastCalledWith(2);
        navButton('paginator-first').click();
        expect(spy).toHaveBeenLastCalledWith(0);
    });

    it('emits pageSizeChange from the rows-per-page select', async () => {
        const spy = vi.spyOn(component.pageSizeChange, 'emit');
        setInputs(130, 0, 50);
        await fixture.whenStable();
        fixture.detectChanges();
        const select: HTMLSelectElement = fixture.debugElement.query(By.css('[data-testid="paginator-page-size"]')).nativeElement;
        expect(select.selectedOptions[0].textContent).toBe('50');
        select.value = Array.from(select.options).find((option) => option.textContent === '20')!.value;
        select.dispatchEvent(new Event('change'));
        expect(spy).toHaveBeenCalledWith(20);
    });

    it('renders the current-page report element', () => {
        setInputs(130, 0);
        expect(fixture.debugElement.query(By.css('[data-testid="paginator-report"]'))).toBeTruthy();
    });

    it('renders windowed page-number buttons (max 5) and marks the current page', () => {
        setInputs(500, 4, 50);
        const pageButtons = fixture.debugElement.queryAll(By.css('[data-testid="paginator-page"]'));
        expect(pageButtons.length).toBe(5);
        const current = pageButtons.find((b) => b.nativeElement.getAttribute('aria-current') === 'page');
        expect(current?.nativeElement.textContent.trim()).toBe('5');
    });

    it('emits pageChange when a page-number button is clicked', () => {
        const spy = vi.spyOn(component.pageChange, 'emit');
        setInputs(500, 4, 50);
        const pageButtons = fixture.debugElement.queryAll(By.css('[data-testid="paginator-page"]'));
        pageButtons[0].nativeElement.click();
        expect(spy).toHaveBeenCalledWith(2);
    });

    it('clamps the display when the page input exceeds the last valid page (no stranded/empty state)', () => {
        setInputs(30, 5, 50);
        expect(navButton('paginator-next').disabled).toBe(true);
        expect(navButton('paginator-last').disabled).toBe(true);
        const pages = fixture.debugElement.queryAll(By.css('[data-testid="paginator-page"]'));
        expect(pages.length).toBe(1);
        expect(pages[0].nativeElement.getAttribute('aria-current')).toBe('page');
    });
});

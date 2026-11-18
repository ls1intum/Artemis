import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective, MockPipe } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FaqImportDialogComponent } from './faq-import-dialog.component';
import { FaqService } from 'app/communication/faq/faq.service';
import { FaqImportCandidate, FaqImportSearchResult, FaqState } from 'app/communication/shared/entities/faq.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const first: FaqImportCandidate = {
    id: 10,
    questionTitle: 'Where are the slides?',
    questionAnswer: 'In the lecture.',
    faqState: FaqState.ACCEPTED,
    courseId: 2,
    courseTitle: 'Previous course',
    courseSemester: 'WS25',
};
const second: FaqImportCandidate = {
    id: 20,
    questionTitle: 'When is the exam?',
    questionAnswer: 'In February.',
    faqState: FaqState.PROPOSED,
    courseId: 3,
    courseTitle: 'Parallel course',
};
const page = (faqs: FaqImportCandidate[], totalElements = 20): FaqImportSearchResult => ({ resultsOnPage: faqs, numberOfPages: 2, totalElements });

describe('FAQ import dialog', () => {
    let fixture: ComponentFixture<FaqImportDialogComponent>;
    let component: FaqImportDialogComponent;
    const service = { searchForImport: vi.fn(), importFaqs: vi.fn() };

    beforeEach(async () => {
        service.searchForImport.mockReset().mockReturnValue(of(page([first])));
        service.importFaqs.mockReset().mockReturnValue(of(new HttpResponse({ body: [{ id: 31 }] })));
        await TestBed.configureTestingModule({
            imports: [FaqImportDialogComponent],
            providers: [
                { provide: FaqService, useValue: service },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(FaqImportDialogComponent, {
                remove: { imports: [MarkdownDirective, ArtemisTranslatePipe] },
                add: { imports: [MockDirective(MarkdownDirective), MockPipe(ArtemisTranslatePipe, (key: string) => key)] },
            })
            .compileComponents();
        fixture = TestBed.createComponent(FaqImportDialogComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 42);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.useRealTimers();
        fixture.destroy();
    });

    function checkbox(id: number): HTMLInputElement {
        return document.body.querySelector<HTMLInputElement>(`#faq-import-select-${id}`)!;
    }

    function select(id: number, checked = true): void {
        const input = checkbox(id);
        input.checked = checked;
        input.dispatchEvent(new Event('change', { bubbles: true }));
        fixture.detectChanges();
    }

    it('renders visible FAQs with their course, answer preview, and disabled import until selected', () => {
        expect(service.searchForImport).toHaveBeenCalledExactlyOnceWith(42, '', 0);
        const row = document.body.querySelector('[data-testid="faq-import-row-10"]');
        expect(row?.textContent).toContain('Where are the slides?');
        expect(row?.textContent).toContain('Previous course');
        expect(row?.textContent).toContain('WS25');
        expect(row?.querySelector('details')).not.toBeNull();
        expect(document.body.querySelector<HTMLButtonElement>('[data-testid="faq-import-submit"] button')?.disabled).toBe(true);
        select(10);
        expect(document.body.querySelector<HTMLButtonElement>('[data-testid="faq-import-submit"] button')?.disabled).toBe(false);
    });

    it('keeps selections across pages and imports exactly the selected IDs', () => {
        select(10);
        service.searchForImport.mockReturnValue(of(page([second])));
        component['loadPage'](1);
        fixture.detectChanges();
        select(20);
        service.searchForImport.mockReturnValue(of(page([first])));
        component['loadPage'](0);
        fixture.detectChanges();
        expect(checkbox(10).checked).toBe(true);
        expect(component['selectedCount']()).toBe(2);
        const emitted = vi.fn();
        component.imported.subscribe(emitted);
        component['importSelected']();
        expect(service.importFaqs).toHaveBeenCalledExactlyOnceWith(42, [10, 20]);
        expect(emitted).toHaveBeenCalledExactlyOnceWith(1);
        expect(component.visible()).toBe(false);
    });

    it('selects and deselects the displayed page without clearing other pages', () => {
        select(10);
        service.searchForImport.mockReturnValue(of(page([second])));
        component['loadPage'](1);
        component['togglePage'](true);
        expect(component['selectedCount']()).toBe(2);
        expect(component['allOnPageSelected']()).toBe(true);
        component['togglePage'](false);
        expect([...component['selectedIds']()]).toEqual([10]);
        component['clearSelection']();
        expect(component['selectedCount']()).toBe(0);
    });

    it('shows a partial select-all state and can deselect an individual FAQ', () => {
        service.searchForImport.mockReturnValue(of(page([first, second])));
        component['loadPage'](0);
        fixture.detectChanges();
        select(10);
        expect(component['someOnPageSelected']()).toBe(true);
        select(10, false);
        expect(component['someOnPageSelected']()).toBe(false);
        expect(component['selectedCount']()).toBe(0);
    });

    it('debounces search, resets pagination, and preserves selection', () => {
        vi.useFakeTimers();
        select(10);
        component['loadPage'](1);
        component['changeSearch'](' exam ');
        component['changeSearch'](' exam dates ');
        vi.advanceTimersByTime(299);
        expect(service.searchForImport).toHaveBeenCalledTimes(2);
        vi.advanceTimersByTime(1);
        expect(service.searchForImport).toHaveBeenLastCalledWith(42, 'exam dates', 0);
        expect(component['selectedCount']()).toBe(1);
    });

    it('cancels older searches and ignores stale results', () => {
        vi.useFakeTimers();
        const older = new Subject<FaqImportSearchResult>();
        service.searchForImport.mockReturnValue(older);
        component['loadPage'](1);
        component['changeSearch']('exam');
        older.next(page([second]));
        expect(component['faqs']()).toEqual([]);
        service.searchForImport.mockReturnValue(of(page([first])));
        vi.advanceTimersByTime(300);
        expect(component['faqs']()).toEqual([first]);
    });

    it('shows an empty state and recovers from a failed search', () => {
        service.searchForImport.mockReturnValue(throwError(() => new Error('Search failed')));
        component['loadPage'](0);
        fixture.detectChanges();
        expect(document.body.querySelector('[role="alert"]')).not.toBeNull();
        expect(component['loading']()).toBe(false);
        service.searchForImport.mockReturnValue(of(page([], 0)));
        component['loadPage'](0);
        fixture.detectChanges();
        expect(document.body.querySelector('tumaet-ui-empty-state')).not.toBeNull();
        expect(component['searchFailed']()).toBe(false);
    });

    it('preserves the selection after import failure and allows retry', () => {
        select(10);
        service.importFaqs.mockReturnValueOnce(throwError(() => new Error('Access changed')));
        component['importSelected']();
        fixture.detectChanges();
        expect(component.visible()).toBe(true);
        expect(component['selectedCount']()).toBe(1);
        expect(document.body.querySelector('[data-testid="faq-import-error"]')).not.toBeNull();
        component['importSelected']();
        expect(component.visible()).toBe(false);
    });

    it('prevents empty and concurrent import requests', () => {
        component['importSelected']();
        expect(service.importFaqs).not.toHaveBeenCalled();
        select(10);
        service.importFaqs.mockReturnValue(new Subject());
        component['importSelected']();
        component['importSelected']();
        fixture.detectChanges();
        expect(service.importFaqs).toHaveBeenCalledTimes(1);
        expect(checkbox(10).disabled).toBe(true);
    });
    it('reports zero imported FAQs when the server returns an empty response', () => {
        select(10);
        service.importFaqs.mockReturnValue(of(new HttpResponse({ body: null })));
        const emitted = vi.fn();
        component.imported.subscribe(emitted);
        component['importSelected']();
        expect(emitted).toHaveBeenCalledExactlyOnceWith(0);
        expect(component.visible()).toBe(false);
    });
});

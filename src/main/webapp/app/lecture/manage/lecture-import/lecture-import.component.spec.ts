import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureImportComponent } from './lecture-import.component';
import { LecturePagingService } from 'app/lecture/manage/services/lecture-paging.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { of } from 'rxjs';

/** The picker works as embedded content; its host owns the dialog lifecycle. */
describe('Lecture import picker', () => {
    let fixture: ComponentFixture<LectureImportComponent>;
    let component: LectureImportComponent;
    const lecture = { id: 7, title: 'Lecture <script>', course: { title: 'Course', semester: 'WS26' } } as Lecture;
    const search = vi.fn(() => of({ resultsOnPage: [lecture], numberOfPages: 3 }));

    beforeEach(async () => {
        search.mockClear();
        await TestBed.configureTestingModule({
            imports: [LectureImportComponent],
            providers: [
                { provide: LecturePagingService, useValue: { search } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(LectureImportComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
        await new Promise((resolve) => setTimeout(resolve, 10));
        fixture.detectChanges();
    });

    afterEach(() => vi.restoreAllMocks());

    it('loads the first page immediately and emits the selected lecture without a dialog provider', () => {
        expect(search).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 10 }));
        const selected = vi.fn();
        component.selected.subscribe(selected);
        const button = fixture.nativeElement.querySelector('[data-testid="import-lecture-7"]') as HTMLButtonElement;
        button.click();
        expect(selected).toHaveBeenCalledWith(lecture);
    });

    it('highlights matching title text safely after searching', async () => {
        const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
        input.value = '<script>';
        input.dispatchEvent(new Event('input'));
        await new Promise((resolve) => setTimeout(resolve, 350));
        await fixture.whenStable();
        fixture.detectChanges();
        expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ searchTerm: '<script>' }));
        expect(fixture.nativeElement.querySelector('mark')?.textContent).toBe('<script>');
        expect(fixture.nativeElement.querySelector('script')).toBeNull();
    });

    it('sorts search results in both directions through the column control', async () => {
        const title = fixture.nativeElement.querySelectorAll('thead th')[1] as HTMLElement;
        const sort = title.querySelector('button');
        expect(sort).not.toBeNull();
        sort!.click();
        await new Promise((resolve) => setTimeout(resolve, 10));
        await fixture.whenStable();
        expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ sortedColumn: 'TITLE', sortingOrder: 'ASCENDING' }));
        expect(title.getAttribute('aria-sort')).toBe('ascending');
        sort!.click();
        await new Promise((resolve) => setTimeout(resolve, 10));
        await fixture.whenStable();
        expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ sortedColumn: 'TITLE', sortingOrder: 'DESCENDING' }));
        expect(title.getAttribute('aria-sort')).toBe('descending');
    });

    it('requests the chosen page using one-based paging', async () => {
        component.onPageChange(2);
        await new Promise((resolve) => setTimeout(resolve, 10));
        expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }));
    });

    it('keeps page navigation without claiming an item count that the API does not provide', async () => {
        const paginator = fixture.nativeElement.querySelector('nav') as HTMLElement;
        expect(paginator.textContent).not.toContain('Showing');
        const lastPage = paginator.querySelector('[aria-label="Last page"]') as HTMLButtonElement;
        lastPage.click();
        await new Promise((resolve) => setTimeout(resolve, 10));
        await fixture.whenStable();
        expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ page: 3 }));
        expect(lastPage.disabled).toBe(true);
    });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { SimpleChange } from '@angular/core';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { PlagiarismSidebarComponent } from 'app/plagiarism/manage/plagiarism-sidebar/plagiarism-sidebar.component';

describe('Plagiarism Sidebar Component', () => {
    let comp: PlagiarismSidebarComponent;
    let fixture: ComponentFixture<PlagiarismSidebarComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [PlagiarismSidebarComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismSidebarComponent);
        comp = fixture.componentInstance;

        comp.pageSize = 10;
    });

    it('displays the run details', () => {
        jest.spyOn(comp.showRunDetailsChange, 'emit');

        comp.displayRunDetails();
        expect(comp.showRunDetailsChange.emit).toHaveBeenCalledWith(true);
    });

    it('computes the number of pages with multiple comparisons', () => {
        const numberOfPages = comp.computeNumberOfPages(12);
        expect(numberOfPages).toBe(2);
    });

    it('computes the number of pages with 0 comparisons', () => {
        const numberOfPages = comp.computeNumberOfPages(0);
        expect(numberOfPages).toBe(0);
    });

    it('computes the number of pages with one comparison', () => {
        const numberOfPages = comp.computeNumberOfPages(1);
        expect(numberOfPages).toBe(1);
    });

    it('computes the number of pages with number of comparisons equal to page size', () => {
        const numberOfPages = comp.computeNumberOfPages(10);
        expect(numberOfPages).toBe(1);
    });

    it('computes the paged index', () => {
        comp.currentPage = 2;
        const pagedIndex = comp.getPagedIndex(1);

        expect(pagedIndex).toBe(21);
    });

    it('pages left', () => {
        comp.currentPage = 2;
        comp.handlePageLeft();

        expect(comp.currentPage).toBe(1);
    });

    it('does not page left', () => {
        comp.currentPage = 0;
        comp.handlePageLeft();

        expect(comp.currentPage).toBe(0);
    });

    it('pages right', () => {
        comp.currentPage = 2;
        comp.numberOfPages = 3;
        comp.handlePageRight();

        expect(comp.currentPage).toBe(2);
    });

    it('does not pages right', () => {
        comp.currentPage = 3;
        comp.numberOfPages = 3;
        comp.handlePageRight();

        expect(comp.currentPage).toBe(3);
    });

    it('should reset pagination on changes', () => {
        const comparisons = [
            { id: 1 } as PlagiarismComparison,
            { id: 2 } as PlagiarismComparison,
            { id: 3 } as PlagiarismComparison,
            { id: 4 } as PlagiarismComparison,
            { id: 5 } as PlagiarismComparison,
            { id: 6 } as PlagiarismComparison,
            { id: 7 } as PlagiarismComparison,
            { id: 8 } as PlagiarismComparison,
            { id: 9 } as PlagiarismComparison,
            { id: 10 } as PlagiarismComparison,
            { id: 11 } as PlagiarismComparison,
            { id: 12 } as PlagiarismComparison,
        ];
        const pagedComparisons = comparisons.slice(0, 10);
        fixture.componentRef.setInput('comparisons', comparisons);
        comp.ngOnChanges({
            comparisons: new SimpleChange([], comparisons, true),
        });
        expect(comp.currentPage).toBe(0);
        expect(comp.numberOfPages).toBe(2);
        expect(comp.pagedComparisons).toEqual(pagedComparisons);
    });
});

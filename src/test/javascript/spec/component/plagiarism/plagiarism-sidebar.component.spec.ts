import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { PlagiarismSidebarComponent } from 'app/exercises/shared/plagiarism/plagiarism-sidebar/plagiarism-sidebar.component';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

describe('Plagiarism Sidebar Component', () => {
    let comp: PlagiarismSidebarComponent;
    let fixture: ComponentFixture<PlagiarismSidebarComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
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
        expect(numberOfPages).toBe(1);
    });

    it('computes the number of pages with 0 comparisons', () => {
        const numberOfPages = comp.computeNumberOfPages(0);
        expect(numberOfPages).toBe(0);
    });

    it('computes the number of pages with one comparison', () => {
        const numberOfPages = comp.computeNumberOfPages(1);
        expect(numberOfPages).toBe(0);
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

        expect(comp.currentPage).toBe(3);
    });

    it('does not pages right', () => {
        comp.currentPage = 3;
        comp.numberOfPages = 3;
        comp.handlePageRight();

        expect(comp.currentPage).toBe(3);
    });

    it('should reset pagination on changes', () => {
        const comparisons = [
            { id: 1 } as PlagiarismComparison<any>,
            { id: 2 } as PlagiarismComparison<any>,
            { id: 3 } as PlagiarismComparison<any>,
            { id: 4 } as PlagiarismComparison<any>,
            { id: 5 } as PlagiarismComparison<any>,
            { id: 6 } as PlagiarismComparison<any>,
            { id: 7 } as PlagiarismComparison<any>,
            { id: 8 } as PlagiarismComparison<any>,
            { id: 9 } as PlagiarismComparison<any>,
            { id: 10 } as PlagiarismComparison<any>,
            { id: 11 } as PlagiarismComparison<any>,
            { id: 12 } as PlagiarismComparison<any>,
        ];
        const pagedComparisons = comparisons.slice(0, 10);
        comp.comparisons = comparisons;
        comp.ngOnChanges({
            comparisons: {
                currentValue: comparisons,
            } as SimpleChange,
        });

        expect(comp.currentPage).toBe(0);
        expect(comp.numberOfPages).toBe(1);
        expect(comp.pagedComparisons).toEqual(pagedComparisons);
    });
});

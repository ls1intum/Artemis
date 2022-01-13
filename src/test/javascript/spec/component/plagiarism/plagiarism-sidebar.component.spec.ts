import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { PlagiarismSidebarComponent } from 'app/exercises/shared/plagiarism/plagiarism-sidebar/plagiarism-sidebar.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { SimpleChange } from '@angular/core';

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
        expect(numberOfPages).toEqual(1);
    });

    it('computes the number of pages with 0 comparisons', () => {
        const numberOfPages = comp.computeNumberOfPages(0);
        expect(numberOfPages).toEqual(0);
    });

    it('computes the number of pages with one comparison', () => {
        const numberOfPages = comp.computeNumberOfPages(1);
        expect(numberOfPages).toEqual(0);
    });

    it('computes the paged index', () => {
        comp.currentPage = 2;
        const pagedIndex = comp.getPagedIndex(1);

        expect(pagedIndex).toEqual(21);
    });

    it('pages left', () => {
        comp.currentPage = 2;
        comp.handlePageLeft();

        expect(comp.currentPage).toEqual(1);
    });

    it('does not page left', () => {
        comp.currentPage = 0;
        comp.handlePageLeft();

        expect(comp.currentPage).toEqual(0);
    });

    it('pages right', () => {
        comp.currentPage = 2;
        comp.numberOfPages = 3;
        comp.handlePageRight();

        expect(comp.currentPage).toEqual(3);
    });

    it('does not pages right', () => {
        comp.currentPage = 3;
        comp.numberOfPages = 3;
        comp.handlePageRight();

        expect(comp.currentPage).toEqual(3);
    });

    it('should handle changes correctly', () => {
        comp.comparisons = [];
        comp.ngOnChanges({
            comparisons: { currentValue: [] } as SimpleChange,
        });

        expect(comp.currentPage).toBe(0);
        expect(comp.numberOfPages).toBe(0);
        expect(comp.pagedComparisons).toEqual([]);
    });
});

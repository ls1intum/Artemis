import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { PlagiarismSidebarComponent } from 'app/plagiarism/manage/plagiarism-sidebar/plagiarism-sidebar.component';

describe('Plagiarism Sidebar Component', () => {
    setupTestBed({ zoneless: true });

    let comp: PlagiarismSidebarComponent;
    let fixture: ComponentFixture<PlagiarismSidebarComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [PlagiarismSidebarComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismSidebarComponent);
        comp = fixture.componentInstance;

        comp.pageSize = 10;
    });

    it('displays the run details', () => {
        vi.spyOn(comp.showRunDetailsChange, 'emit');

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
        comp.currentPage.set(2);
        const pagedIndex = comp.getPagedIndex(1);

        expect(pagedIndex).toBe(21);
    });

    it('pages left', () => {
        comp.currentPage.set(2);
        comp.handlePageLeft();

        expect(comp.currentPage()).toBe(1);
    });

    it('does not page left', () => {
        comp.currentPage.set(0);
        comp.handlePageLeft();

        expect(comp.currentPage()).toBe(0);
    });

    it('pages right', () => {
        comp.currentPage.set(2);
        comp.numberOfPages.set(3);
        comp.handlePageRight();

        expect(comp.currentPage()).toBe(2);
    });

    it('does not pages right', () => {
        comp.currentPage.set(3);
        comp.numberOfPages.set(3);
        comp.handlePageRight();

        expect(comp.currentPage()).toBe(3);
    });

    it('should reset pagination on changes', () => {
        // submissionA/submissionB are populated so the rendered template (driven by detectChanges) can read studentLogin.
        const comparisons = Array.from({ length: 12 }, (_, index) => ({ id: index + 1, submissionA: {}, submissionB: {} }) as PlagiarismComparison);
        const pagedComparisons = comparisons.slice(0, 10);

        // The constructor effect resets paging whenever comparisons() changes (replaces the former ngOnChanges).
        fixture.componentRef.setInput('comparisons', comparisons);
        fixture.detectChanges();

        expect(comp.currentPage()).toBe(0);
        expect(comp.numberOfPages()).toBe(2);
        expect(comp.pagedComparisons()).toEqual(pagedComparisons);
    });
});

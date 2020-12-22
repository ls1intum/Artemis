import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';

@Component({
    selector: 'jhi-plagiarism-sidebar',
    styleUrls: ['./plagiarism-sidebar.component.scss'],
    templateUrl: './plagiarism-sidebar.component.html',
})
export class PlagiarismSidebarComponent implements OnChanges {
    @Input() activeIndex: number;
    @Input() comparisons?: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>[];

    @Output() selectIndex = new EventEmitter<number>();

    /**
     * Index of the currently selected result page.
     */
    public currentPage = 0;

    /**
     * Total number of result pages.
     */
    public numberOfPages = 0;

    /**
     * Subset of currently paged comparisons.
     */
    public pagedComparisons?: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>[];

    /**
     * Number of comparisons per page.
     */
    public pageSize = 10;

    ngOnChanges(changes: SimpleChanges) {
        if (changes.comparisons) {
            const comparisons: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>[] = changes.comparisons.currentValue;

            this.currentPage = 0;
            this.numberOfPages = this.computeNumberOfPages(comparisons.length);
            this.pagedComparisons = this.getPagedComparisons();
        }
    }

    computeNumberOfPages(totalComparisons: number) {
        return Math.floor(totalComparisons / this.pageSize);
    }

    getPagedComparisons() {
        const startIndex = this.currentPage * this.pageSize;

        return this.comparisons?.slice(startIndex, startIndex + this.pageSize);
    }

    getPagedIndex(idx: number) {
        return idx + this.currentPage * this.pageSize;
    }

    handlePageLeft() {
        if (this.currentPage === 0) {
            return;
        }

        this.currentPage--;
        this.pagedComparisons = this.getPagedComparisons();
    }

    handlePageRight() {
        if (this.currentPage === this.numberOfPages) {
            return;
        }

        this.currentPage++;
        this.pagedComparisons = this.getPagedComparisons();
    }
}

import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { faArrowLeft, faArrowRight, faChevronRight, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-plagiarism-sidebar',
    styleUrls: ['./plagiarism-sidebar.component.scss'],
    templateUrl: './plagiarism-sidebar.component.html',
})
export class PlagiarismSidebarComponent implements OnChanges {
    @Input() activeID: number;
    @Input() comparisons?: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>[];
    @Input() casesFiltered = false;
    @Input() offset = 0;

    @Input() showRunDetails: boolean;
    @Output() showRunDetailsChange = new EventEmitter<boolean>();

    @Output() selectIndex = new EventEmitter<number>();

    readonly CONFIRMED = PlagiarismStatus.CONFIRMED;
    readonly DENIED = PlagiarismStatus.DENIED;

    faExclamationTriangle = faExclamationTriangle;

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
    public pageSize = 100;

    // Icons
    faChevronRight = faChevronRight;
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;

    ngOnChanges(changes: SimpleChanges) {
        if (changes.comparisons) {
            const comparisons: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>[] = changes.comparisons.currentValue;

            this.currentPage = 0;
            if (!comparisons) {
                this.numberOfPages = 0;
            } else {
                this.numberOfPages = this.computeNumberOfPages(comparisons.length);
            }
            this.pagedComparisons = this.getPagedComparisons();
        }
    }

    displayRunDetails() {
        this.showRunDetailsChange.emit(true);
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

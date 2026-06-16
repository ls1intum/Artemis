import { Component, OnChanges, SimpleChanges, input, output, signal } from '@angular/core';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { faArrowLeft, faArrowRight, faChevronRight, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DecimalPipe, NgClass } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-plagiarism-sidebar',
    styleUrls: ['./plagiarism-sidebar.component.scss'],
    templateUrl: './plagiarism-sidebar.component.html',
    imports: [FaIconComponent, TranslateDirective, NgClass, DecimalPipe, ArtemisTranslatePipe],
})
export class PlagiarismSidebarComponent implements OnChanges {
    readonly activeID = input<number>();
    readonly comparisons = input<PlagiarismComparison[]>();
    readonly casesFiltered = input(false);
    readonly offset = input(0);

    readonly showRunDetails = input<boolean>();
    readonly showRunDetailsChange = output<boolean>();

    readonly selectIndex = output<number>();

    readonly CONFIRMED = PlagiarismStatus.CONFIRMED;
    readonly DENIED = PlagiarismStatus.DENIED;

    faExclamationTriangle = faExclamationTriangle;

    /**
     * Index of the currently selected result page.
     */
    public readonly currentPage = signal(0);

    /**
     * Total number of result pages.
     */
    public readonly numberOfPages = signal(0);

    /**
     * Subset of currently paged comparisons.
     */
    public readonly pagedComparisons = signal<PlagiarismComparison[] | undefined>(undefined);

    /**
     * Number of comparisons per page.
     */
    public pageSize = 100;

    // Icons
    faChevronRight = faChevronRight;
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;

    ngOnChanges(changes: SimpleChanges) {
        if (changes.comparisons?.currentValue !== changes.comparisons?.previousValue) {
            const comparisons: PlagiarismComparison[] = changes.comparisons.currentValue;

            this.currentPage.set(0);
            if (!comparisons) {
                this.numberOfPages.set(1);
            } else {
                this.numberOfPages.set(this.computeNumberOfPages(comparisons.length));
            }
            this.pagedComparisons.set(this.getPagedComparisons());
        }
    }

    displayRunDetails() {
        this.showRunDetailsChange.emit(true);
    }

    computeNumberOfPages(totalComparisons: number) {
        return Math.ceil(totalComparisons / this.pageSize);
    }

    getPagedComparisons() {
        const startIndex = this.currentPage() * this.pageSize;
        return this.comparisons()?.slice(startIndex, startIndex + this.pageSize);
    }

    getPagedIndex(idx: number) {
        return idx + this.currentPage() * this.pageSize;
    }

    handlePageLeft() {
        if (this.currentPage() === 0) {
            return;
        }

        this.currentPage.update((page) => page - 1);
        this.pagedComparisons.set(this.getPagedComparisons());
    }

    handlePageRight() {
        if (this.currentPage() + 1 >= this.numberOfPages()) {
            return;
        }

        this.currentPage.update((page) => page + 1);
        this.pagedComparisons.set(this.getPagedComparisons());
    }
}

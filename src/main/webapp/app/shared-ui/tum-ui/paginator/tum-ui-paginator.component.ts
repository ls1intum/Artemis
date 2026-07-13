import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faAngleLeft, faAngleRight, faAnglesLeft, faAnglesRight } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';

/**
 * Owned paginator for {@link TumUiTableComponent}, part of the tum-aet-ui kit.
 * Signal-based, PrimeNG-free: first/prev/next/last controls (tum-ui-button), a native rows-per-page
 * select, and a "Showing X to Y of Z" report. Pages are 0-based.
 */
@Component({
    selector: 'tum-ui-paginator',
    templateUrl: './tum-ui-paginator.component.html',
    imports: [TumUiButtonComponent, FaIconComponent, ArtemisTranslatePipe, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiPaginatorComponent {
    readonly totalRecords = input(0);
    readonly page = input(0);
    readonly pageSize = input(50);
    readonly pageSizeOptions = input<number[]>([10, 20, 50, 100, 200]);
    readonly disabled = input(false);

    readonly pageChange = output<number>();
    readonly pageSizeChange = output<number>();

    protected readonly faAnglesLeft = faAnglesLeft;
    protected readonly faAngleLeft = faAngleLeft;
    protected readonly faAngleRight = faAngleRight;
    protected readonly faAnglesRight = faAnglesRight;

    protected readonly totalPages = computed(() => Math.max(1, Math.ceil(this.totalRecords() / Math.max(1, this.pageSize()))));
    protected readonly isFirst = computed(() => this.page() <= 0);
    protected readonly isLast = computed(() => this.page() >= this.totalPages() - 1);
    protected readonly rangeBegin = computed(() => (this.totalRecords() === 0 ? 0 : this.page() * this.pageSize() + 1));
    protected readonly rangeEnd = computed(() => Math.min(this.totalRecords(), (this.page() + 1) * this.pageSize()));

    protected goToFirst(): void {
        if (!this.isFirst()) {
            this.pageChange.emit(0);
        }
    }

    protected goToPrevious(): void {
        if (!this.isFirst()) {
            this.pageChange.emit(this.page() - 1);
        }
    }

    protected goToNext(): void {
        if (!this.isLast()) {
            this.pageChange.emit(this.page() + 1);
        }
    }

    protected goToLast(): void {
        if (!this.isLast()) {
            this.pageChange.emit(this.totalPages() - 1);
        }
    }

    protected onPageSizeChange(value: string): void {
        this.pageSizeChange.emit(Number(value));
    }
}

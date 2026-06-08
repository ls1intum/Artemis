import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/**
 * A component that will take care of item count statistics of a pagination.
 */
@Component({
    selector: 'jhi-item-count',
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: ` <div jhiTranslate="global.item-count" [translateValues]="{ first: itemRangeBegin(), second: itemRangeEnd(), total: itemTotal() }"></div> `,
    imports: [TranslateDirective],
})
export class ItemCountComponent {
    /**
     * Pagination parameters:
     *   page          Current page number
     *   totalItems    Total number of items
     *   itemsPerPage  Number of items per page
     */
    readonly params = input<{ page: number; totalItems: number; itemsPerPage: number }>();

    readonly itemRangeBegin = computed(() => {
        const params = this.params();
        if (!params || params.totalItems === 0) {
            return 0;
        }
        return (params.page - 1) * params.itemsPerPage + 1;
    });

    readonly itemRangeEnd = computed(() => {
        const params = this.params();
        if (!params) {
            return 0;
        }
        return Math.min(params.page * params.itemsPerPage, params.totalItems);
    });

    readonly itemTotal = computed(() => this.params()?.totalItems ?? 0);
}

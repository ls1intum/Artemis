import { Component, Input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

/**
 * A component that will take care of item count statistics of a pagination.
 */
@Component({
    selector: 'jhi-item-count',
    template: ` <div jhiTranslate="global.item-count" [translateValues]="{ first: itemRangeBegin, second: itemRangeEnd, total: itemTotal }"></div> `,
    imports: [TranslateDirective],
})
export class ItemCountComponent {
    /**
     * @param params  Contains parameters for component:
     *                    page          Current page number
     *                    totalItems    Total number of items
     *                    itemsPerPage  Number of items per page
     */
    @Input() set params(params: { page: number; totalItems: number; itemsPerPage: number }) {
        this.itemRangeBegin = params.totalItems === 0 ? 0 : (params.page - 1) * params.itemsPerPage + 1;
        this.itemRangeEnd = Math.min(params.page * params.itemsPerPage, params.totalItems);
        this.itemTotal = params.totalItems;
    }

    itemRangeBegin = 0;
    itemRangeEnd = 0;
    itemTotal = 0;
}

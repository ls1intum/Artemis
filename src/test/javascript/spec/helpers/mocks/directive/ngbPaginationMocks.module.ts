import { Component, Directive, EventEmitter, Input, NgModule, Output } from '@angular/core';

// eslint-disable-next-line @angular-eslint/directive-selector
@Directive({ selector: 'ng-template[ngbPaginationLast]' })
export class NgbPaginationLastMockDirective {}
// eslint-disable-next-line @angular-eslint/directive-selector
@Directive({ selector: 'ng-template[ngbPaginationNext]' })
export class NgbPaginationNextMockDirective {}

// eslint-disable-next-line @angular-eslint/directive-selector
@Directive({ selector: 'ng-template[ngbPaginationNumber]' })
export class NgbPaginationNumberMockDirective {}

// eslint-disable-next-line @angular-eslint/directive-selector
@Directive({ selector: 'ng-template[ngbPaginationPrevious]' })
export class NgbPaginationPreviousMockDirective {}

// eslint-disable-next-line @angular-eslint/directive-selector
@Directive({ selector: 'ng-template[ngbPaginationPages]' })
export class NgbPaginationPagesMockDirective {}

@Component({
    // eslint-disable-next-line @angular-eslint/component-selector
    selector: 'ngb-pagination',
    template: '',
})
export class NgbPaginationMockComponent {
    @Input() disabled: boolean;

    @Input() boundaryLinks: boolean;

    @Input() directionLinks: boolean;
    @Input() ellipses: boolean;
    @Input() rotate: boolean;

    @Input() collectionSize: number;

    @Input() maxSize: number;

    @Input() page = 1;

    @Input() pageSize: number;

    @Output() pageChange = new EventEmitter<number>(true);

    @Input() size: 'sm' | 'lg' | string | null;
}

@NgModule({
    declarations: [
        NgbPaginationMockComponent,
        NgbPaginationPagesMockDirective,
        NgbPaginationLastMockDirective,
        NgbPaginationNextMockDirective,
        NgbPaginationNumberMockDirective,
        NgbPaginationPreviousMockDirective,
    ],
    exports: [
        NgbPaginationMockComponent,
        NgbPaginationPagesMockDirective,
        NgbPaginationLastMockDirective,
        NgbPaginationNextMockDirective,
        NgbPaginationNumberMockDirective,
        NgbPaginationPreviousMockDirective,
    ],
})
export class NgbPaginationMocksModule {}

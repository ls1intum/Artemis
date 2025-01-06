import { Component, Directive, EventEmitter, Input, NgModule, Output } from '@angular/core';

@Directive({ selector: 'ng-template[ngbPaginationLast]' })
export class NgbPaginationLastMockDirective {}

@Directive({ selector: 'ng-template[ngbPaginationNext]' })
export class NgbPaginationNextMockDirective {}

@Directive({ selector: 'ng-template[ngbPaginationNumber]' })
export class NgbPaginationNumberMockDirective {}

@Directive({ selector: 'ng-template[ngbPaginationPrevious]' })
export class NgbPaginationPreviousMockDirective {}

@Directive({ selector: 'ng-template[ngbPaginationPages]' })
export class NgbPaginationPagesMockDirective {}

@Component({
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
    imports: [
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

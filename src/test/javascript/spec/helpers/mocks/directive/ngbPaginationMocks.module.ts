import { Component, Directive, EventEmitter, Input, NgModule, Output } from '@angular/core';

// eslint-disable-next-line @angular-eslint/directive-selector
@Directive({ selector: 'ng-template[ngbPaginationLast]' })
// eslint-disable-next-line @angular-eslint/directive-class-suffix
export class NgbPaginationLastMock {}
// eslint-disable-next-line @angular-eslint/directive-selector
@Directive({ selector: 'ng-template[ngbPaginationNext]' })
// eslint-disable-next-line @angular-eslint/directive-class-suffix
export class NgbPaginationNextMock {}

// eslint-disable-next-line @angular-eslint/directive-selector
@Directive({ selector: 'ng-template[ngbPaginationNumber]' })
// eslint-disable-next-line @angular-eslint/directive-class-suffix
export class NgbPaginationNumberMock {}

// eslint-disable-next-line @angular-eslint/directive-selector
@Directive({ selector: 'ng-template[ngbPaginationPrevious]' })
// eslint-disable-next-line @angular-eslint/directive-class-suffix
export class NgbPaginationPreviousMock {}

// eslint-disable-next-line @angular-eslint/directive-selector
@Directive({ selector: 'ng-template[ngbPaginationPages]' })
// eslint-disable-next-line @angular-eslint/directive-class-suffix
export class NgbPaginationPagesMock {}

@Component({
    // eslint-disable-next-line @angular-eslint/component-selector
    selector: 'ngb-pagination',
    template: '',
})
// eslint-disable-next-line @angular-eslint/component-class-suffix
export class NgbPaginationMock {
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
    declarations: [NgbPaginationMock, NgbPaginationPagesMock, NgbPaginationLastMock, NgbPaginationNextMock, NgbPaginationNumberMock, NgbPaginationPreviousMock],
    exports: [NgbPaginationMock, NgbPaginationPagesMock, NgbPaginationLastMock, NgbPaginationNextMock, NgbPaginationNumberMock, NgbPaginationPreviousMock],
})
export class NgbPaginationMocksModule {}

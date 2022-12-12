import { Component, Directive, EventEmitter, Input, NgModule, Output } from '@angular/core';

@Directive({ selector: 'ng-template[ngbPaginationLast]' })
export class NgbPaginationLastMock {}
@Directive({ selector: 'ng-template[ngbPaginationNext]' })
export class NgbPaginationNextMock {}

@Directive({ selector: 'ng-template[ngbPaginationNumber]' })
export class NgbPaginationNumberMock {}

@Directive({ selector: 'ng-template[ngbPaginationPrevious]' })
export class NgbPaginationPreviousMock {}

@Directive({ selector: 'ng-template[ngbPaginationPages]' })
export class NgbPaginationPagesMock {}

@Component({
    selector: 'ngb-pagination',
    template: '',
})
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

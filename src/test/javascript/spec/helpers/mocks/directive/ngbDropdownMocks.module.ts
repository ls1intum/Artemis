import { Directive, EventEmitter, Input, NgModule, Output } from '@angular/core';

@Directive({
    selector: '[ngbDropdownItem]',
})
class NgbDropdownItemMock {}

@Directive({
    selector: '[ngbDropdownMenu]',
})
class NgbDropdownMenuMock {}

@Directive({
    selector: '[ngbDropdownToggle]',
})
class NgbDropdownToggleMock {}

@Directive({
    selector: '[ngbDropdown]',
    exportAs: 'ngbDropdown',
})
class NgbDropdownMock {
    @Input() autoClose: boolean | 'outside' | 'inside';
    @Input() dropdownClass: string;
    @Input('open') _open = false;
    @Input() placement: any;
    @Input() popperOptions: (options: Partial<any>) => Partial<any>;
    @Input() container: null | 'body';
    @Input() display: 'dynamic' | 'static';
    @Output() openChange = new EventEmitter<boolean>();
}

@Directive({
    selector: '[ngbDropdownAnchor]',
})
class NgbDropdownAnchorMock {}

@NgModule({
    declarations: [NgbDropdownItemMock, NgbDropdownMenuMock, NgbDropdownToggleMock, NgbDropdownMock, NgbDropdownAnchorMock],
    exports: [NgbDropdownItemMock, NgbDropdownMenuMock, NgbDropdownToggleMock, NgbDropdownMock, NgbDropdownAnchorMock],
})
export class NgbDropdownMocksModule {}

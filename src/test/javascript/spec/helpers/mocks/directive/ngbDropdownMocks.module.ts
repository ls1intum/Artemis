import { Directive, NgModule } from '@angular/core';

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
class NgbDropdownMock {}

@Directive({
    selector: '[ngbDropdownAnchor]',
})
class NgbDropdownAnchorMock {}

@NgModule({
    declarations: [NgbDropdownItemMock, NgbDropdownMenuMock, NgbDropdownToggleMock, NgbDropdownMock, NgbDropdownAnchorMock],
    exports: [NgbDropdownItemMock, NgbDropdownMenuMock, NgbDropdownToggleMock, NgbDropdownMock, NgbDropdownAnchorMock],
})
export class NgbDropdownMocksModule {}

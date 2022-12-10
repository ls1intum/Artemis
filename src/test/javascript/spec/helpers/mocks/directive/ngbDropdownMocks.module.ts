import { Directive, EventEmitter, Input, NgModule, Output } from '@angular/core';
import { PlacementArray } from '@ng-bootstrap/ng-bootstrap/util/positioning';
import { Options } from '@popperjs/core';

@Directive({
    selector: '[ngbDropdownItem]',
})
class NgbDropdownItemMock {
    @Input() disabled: boolean;
}

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
    @Input() placement: PlacementArray;
    @Input() popperOptions: (options: Partial<Options>) => Partial<Options>;
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

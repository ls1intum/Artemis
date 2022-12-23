import { Directive, EventEmitter, Input, NgModule, Output } from '@angular/core';

@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[ngbDropdownItem]',
})
class NgbDropdownItemMockDirective {
    @Input() disabled: boolean;
}

@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[ngbDropdownMenu]',
})
class NgbDropdownMenuMockDirective {}

@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[ngbDropdownToggle]',
})
class NgbDropdownToggleMockDirective {}

@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[ngbDropdown]',
    exportAs: 'ngbDropdown',
})
class NgbDropdownMockDirective {
    @Input() autoClose: boolean | 'outside' | 'inside';
    @Input() dropdownClass: string;
    @Input() open = false;
    @Input() placement: any;
    @Input() popperOptions: (options: Partial<any>) => Partial<any>;
    @Input() container: null | 'body';
    @Input() display: 'dynamic' | 'static';
    @Output() openChange = new EventEmitter<boolean>();
}

@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[ngbDropdownAnchor]',
})
class NgbDropdownAnchorMockDirective {}

@NgModule({
    declarations: [NgbDropdownItemMockDirective, NgbDropdownMenuMockDirective, NgbDropdownToggleMockDirective, NgbDropdownMockDirective, NgbDropdownAnchorMockDirective],
    exports: [NgbDropdownItemMockDirective, NgbDropdownMenuMockDirective, NgbDropdownToggleMockDirective, NgbDropdownMockDirective, NgbDropdownAnchorMockDirective],
})
export class NgbDropdownMocksModule {}

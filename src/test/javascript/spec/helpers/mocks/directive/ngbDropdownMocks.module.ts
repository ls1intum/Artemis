import { Directive, EventEmitter, Input, NgModule, Output } from '@angular/core';

@Directive({
    selector: '[ngbDropdownItem]',
})
class NgbDropdownItemMockDirective {
    @Input() disabled: boolean;
}

@Directive({
    selector: '[ngbDropdownMenu]',
})
class NgbDropdownMenuMockDirective {}

@Directive({
    selector: '[ngbDropdownToggle]',
})
class NgbDropdownToggleMockDirective {}

@Directive({
    selector: '[ngbDropdown]',
    exportAs: 'ngbDropdown',
})
class NgbDropdownMockDirective {
    @Input() autoClose: boolean | 'outside' | 'inside';
    @Input() dropdownClass: string;
    @Input() isOpen = false;
    @Input() placement: any;
    @Input() popperOptions: (options: Partial<any>) => Partial<any>;
    @Input() container: null | 'body';
    @Input() display: 'dynamic' | 'static';
    @Output() openChange = new EventEmitter<boolean>();
    open() {
        this.isOpen = true;
        this.openChange.emit(this.isOpen);
    }
    close() {
        this.isOpen = false;
        this.openChange.emit(this.isOpen);
    }
}

@Directive({
    selector: '[ngbDropdownAnchor]',
})
class NgbDropdownAnchorMockDirective {}

@NgModule({
    declarations: [NgbDropdownItemMockDirective, NgbDropdownMenuMockDirective, NgbDropdownToggleMockDirective, NgbDropdownMockDirective, NgbDropdownAnchorMockDirective],
    exports: [NgbDropdownItemMockDirective, NgbDropdownMenuMockDirective, NgbDropdownToggleMockDirective, NgbDropdownMockDirective, NgbDropdownAnchorMockDirective],
})
export class NgbDropdownMocksModule {}

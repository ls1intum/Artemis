import { Directive, HostBinding } from '@angular/core';
import { DropdownDirective } from './dropdown.directive';

@Directive({
    // tslint:disable-next-line:directive-selector
    selector: '[artemisDropdownMenu]',
    host: {
        '[class.dropdown-menu]': 'true',
        '[class.show]': 'dropdown.isOpen',
    },
})
export class DropdownMenuDirective {
    constructor(public dropdown: DropdownDirective) {}
}

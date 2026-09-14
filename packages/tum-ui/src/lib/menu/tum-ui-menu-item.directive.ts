import { Directive } from '@angular/core';
import { CdkMenuItem } from '@angular/cdk/menu';

/**
 * A single command or navigation entry inside a {@link TumUiMenuComponent}.
 *
 * Apply it to a `<button>` for an action, or to an `<a>` for navigation so the entry keeps native link
 * behaviour such as opening in a new tab. `disabled` and the `triggered` output come from the CDK menu item,
 * which also owns the `role`, roving `tabindex`, and closing the menu once an entry runs.
 */
@Directive({
    selector: '[tumUiMenuItem]',
    hostDirectives: [
        {
            directive: CdkMenuItem,
            inputs: ['cdkMenuItemDisabled: disabled'],
            outputs: ['cdkMenuItemTriggered: triggered'],
        },
    ],
    host: {
        class:
            'tum-ui-menu-item tum:flex tum:cursor-pointer tum:items-center tum:gap-2 tum:border-0 tum:bg-transparent tum:px-3 tum:py-2 tum:text-start tum:text-base tum:text-text tum:no-underline ' +
            'tum:hover:bg-hover-background tum:hover:text-text-hover tum:focus-visible:bg-highlight-focus-background tum:focus-visible:text-highlight tum:focus-visible:outline-none ' +
            'tum:aria-disabled:pointer-events-none tum:aria-disabled:cursor-default tum:aria-disabled:text-disabled',
    },
})
export class TumUiMenuItemDirective {}

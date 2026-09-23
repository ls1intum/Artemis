import { Directive } from '@angular/core';
import { CdkMenuTrigger } from '@angular/cdk/menu';

/**
 * Opens a {@link TumUiMenuComponent} from the element it sits on, which is normally a button.
 *
 * Point it at the `ng-template` that holds the menu: `<button [tumUiMenuTrigger]="actions">`. The CDK menu
 * trigger owns the overlay, `aria-haspopup` / `aria-expanded`, opening on Enter, Space, or ArrowDown, closing
 * on Escape or an outside click, and restoring focus to the trigger afterwards.
 */
@Directive({
    selector: '[tumUiMenuTrigger]',
    hostDirectives: [
        {
            directive: CdkMenuTrigger,
            inputs: ['cdkMenuTriggerFor: tumUiMenuTrigger', 'cdkMenuPosition: tumUiMenuPosition'],
            outputs: ['cdkMenuOpened: menuOpened', 'cdkMenuClosed: menuClosed'],
        },
    ],
})
export class TumUiMenuTriggerDirective {}

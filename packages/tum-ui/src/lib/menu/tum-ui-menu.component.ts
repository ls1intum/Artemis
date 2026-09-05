import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CdkMenu } from '@angular/cdk/menu';

/**
 * Menu surface for a list of actions, opened by {@link TumUiMenuTriggerDirective} and filled with
 * `[tumUiMenuItem]` entries.
 *
 * The roles, arrow-key navigation, typeahead, focus handling, and close-on-Escape / close-on-outside-click
 * behavior come from the CDK menu; this component only owns the surface styling. Declare it inside the
 * `ng-template` the trigger points at, so nothing renders until the menu opens:
 *
 * ```html
 * <button [tumUiMenuTrigger]="actions">Actions</button>
 * <ng-template #actions>
 *     <tum-ui-menu>
 *         <a tumUiMenuItem routerLink="./students">Add students</a>
 *         <button tumUiMenuItem (triggered)="archive()">Archive</button>
 *     </tum-ui-menu>
 * </ng-template>
 * ```
 *
 * Use `tum-ui-popover` instead for rich or non-action content: a menu is for commands and navigation.
 */
@Component({
    selector: 'tum-ui-menu',
    template: '<ng-content />',
    styleUrl: './tum-ui-menu.component.scss',
    hostDirectives: [CdkMenu],
    host: {
        class: 'tum-ui-menu tum:flex tum:min-w-48 tum:flex-col tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:py-1 tum:text-text tum:shadow-md',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiMenuComponent {}

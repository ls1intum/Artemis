import { CanDeactivate } from '@angular/router';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';

/**
 * Similar to the pending changes guard, but it does not provide a warning message.
 * The warning message can then be provided by the component.
 */
@Injectable({ providedIn: 'root' })
export class CanDeactivateGuard implements CanDeactivate<ComponentCanDeactivate> {
    /**
     * Function which returns whether the component can be deactivated
     * @param component
     * @returns boolean | Observable<boolean>
     */
    canDeactivate(component: ComponentCanDeactivate): boolean | Observable<boolean> {
        return component.canDeactivate();
    }
}

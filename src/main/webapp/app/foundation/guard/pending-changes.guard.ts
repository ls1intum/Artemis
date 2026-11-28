import { CanDeactivate } from '@angular/router';
import { Service, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ComponentCanDeactivate } from 'app/foundation/guard/can-deactivate.model';

@Service()
export class PendingChangesGuard implements CanDeactivate<ComponentCanDeactivate> {
    private translateService = inject(TranslateService);

    /**
     * Function which returns whether a component can be deactivated
     * @param component the component to leave. Angular passes `null` when the route has no active component, e.g. while it is being destroyed, and there are no
     *                  pending changes to protect then.
     *
     * @returns boolean | Observable<boolean>
     */
    canDeactivate(component: ComponentCanDeactivate | null): boolean {
        if (!component) {
            return true;
        }
        const warning = component.canDeactivateWarning || this.translateService.instant('pendingChanges');
        return component.canDeactivate() ? true : confirm(warning);
    }
}

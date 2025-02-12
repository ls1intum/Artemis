import { CanDeactivate } from '@angular/router';
import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';

@Injectable({ providedIn: 'root' })
export class PendingChangesGuard implements CanDeactivate<ComponentCanDeactivate> {
    private translateService = inject(TranslateService);

    /**
     * Function which returns whether a component can be deactivated
     * @param component
     *
     * @returns boolean | Observable<boolean>
     */
    canDeactivate(component: ComponentCanDeactivate): boolean {
        const warning = component.canDeactivateWarning || this.translateService.instant('pendingChanges');
        return component.canDeactivate() ? true : confirm(warning);
    }
}

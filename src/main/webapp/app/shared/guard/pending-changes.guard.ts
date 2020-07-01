import { CanDeactivate } from '@angular/router';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { TranslateService } from '@ngx-translate/core';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';

@Injectable({ providedIn: 'root' })
export class PendingChangesGuard implements CanDeactivate<ComponentCanDeactivate> {
    constructor(private translateService: TranslateService) {}

    /**
     * Function which returns whether a component can be deactivated
     * @param component
     *
     * @returns boolean | Observable<boolean>
     */
    canDeactivate(component: ComponentCanDeactivate): boolean | Observable<boolean> {
        const warning = component.canDeactivateWarning || this.translateService.instant('pendingChanges');
        return component.canDeactivate() ? true : confirm(warning);
    }
}

import { CanDeactivate } from '@angular/router';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { TranslateService } from '@ngx-translate/core';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';

@Injectable({ providedIn: 'root' })
export class PendingChangesGuard implements CanDeactivate<ComponentCanDeactivate> {
    constructor(private translateService: TranslateService) {}

    /**
     * Wrapper function for {@link ComponentCanDeactivate.canDeactivate} which returns whether the component can be deactivated safely.
     * @method
     * @param {ComponentCanDeactivate} component The component which should deactivate.
     * @returns boolean | Observable<boolean>
     */
    canDeactivate(component: ComponentCanDeactivate): boolean | Observable<boolean> {
        const warning = this.translateService.instant('pendingChanges');
        return component.canDeactivate() ? true : confirm(warning);
    }
}

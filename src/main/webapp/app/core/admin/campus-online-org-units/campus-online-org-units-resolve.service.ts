import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, ResolveFn } from '@angular/router';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AlertService } from 'app/shared/service/alert.service';
import { CampusOnlineOrgUnit, CampusOnlineService } from 'app/core/course/manage/services/campus-online.service';

const DEFAULT_ORG_UNIT: CampusOnlineOrgUnit = { externalId: '', name: '' };

export const campusOnlineOrgUnitsResolve: ResolveFn<CampusOnlineOrgUnit> = (route: ActivatedRouteSnapshot) => {
    if (route.params['id']) {
        const alertService = inject(AlertService);
        return inject(CampusOnlineService)
            .getOrgUnit(route.params['id'])
            .pipe(
                catchError((error) => {
                    alertService.error(error.error?.message ?? error.message);
                    return of(DEFAULT_ORG_UNIT);
                }),
            );
    }
    return DEFAULT_ORG_UNIT;
};

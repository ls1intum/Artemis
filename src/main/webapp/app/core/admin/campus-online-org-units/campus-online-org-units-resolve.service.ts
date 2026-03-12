import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, ResolveFn } from '@angular/router';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AlertService } from 'app/shared/service/alert.service';
import { CampusOnlineOrgUnit, CampusOnlineService } from 'app/core/course/manage/services/campus-online.service';

const DEFAULT_ORG_UNIT: CampusOnlineOrgUnit = { externalId: '', name: '' };

export const campusOnlineOrgUnitsResolve: ResolveFn<CampusOnlineOrgUnit> = (route: ActivatedRouteSnapshot) => {
    const idParam = route.params['id'];
    if (idParam) {
        const alertService = inject(AlertService);
        return inject(CampusOnlineService)
            .getOrgUnit(Number(idParam))
            .pipe(
                catchError((error) => {
                    alertService.error(error.error?.message ?? error.message);
                    return of(DEFAULT_ORG_UNIT);
                }),
            );
    }
    return DEFAULT_ORG_UNIT;
};

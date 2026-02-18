import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, ResolveFn } from '@angular/router';
import { CampusOnlineOrgUnit, CampusOnlineService } from 'app/core/course/manage/services/campus-online.service';

export const campusOnlineOrgUnitsResolve: ResolveFn<CampusOnlineOrgUnit> = (route: ActivatedRouteSnapshot) => {
    if (route.params['id']) {
        return inject(CampusOnlineService).getOrgUnit(route.params['id']);
    }
    return { externalId: '', name: '' } as CampusOnlineOrgUnit;
};

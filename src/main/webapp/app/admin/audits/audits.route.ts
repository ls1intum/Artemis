import { Route } from '@angular/router';
import { AuditsComponent } from 'app/admin/audits/audits.component';

export const auditsRoute: Route = {
    path: 'audits',
    component: AuditsComponent,
    data: {
        pageTitle: 'audits.title',
        defaultSort: 'auditEventDate,desc',
    },
};

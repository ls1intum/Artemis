import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { adminState } from './admin.route';
import { ArtemisSharedModule } from 'app/shared';
import {
    AuditsComponent,
    JhiConfigurationComponent,
    JhiHealthCheckComponent,
    JhiHealthModalComponent,
    JhiMetricsMonitoringComponent,
    JhiTrackerComponent,
    LogsComponent,
    NotificationMgmtComponent,
    NotificationMgmtDetailComponent,
    NotificationMgmtUpdateComponent,
    UserManagementComponent,
    UserManagementDetailComponent,
    UserManagementUpdateComponent,
} from './';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { AdminFeatureToggleComponent } from 'app/admin/features/admin-feature-toggle.component';

/* jhipster-needle-add-admin-module-import - JHipster will add admin modules imports here */

const ENTITY_STATES = [...adminState];

@NgModule({
    imports: [
        RouterModule.forChild(ENTITY_STATES),
        ArtemisSharedModule,
        FormDateTimePickerModule,
        /* jhipster-needle-add-admin-module - JHipster will add admin modules here */
    ],
    declarations: [
        AuditsComponent,
        UserManagementComponent,
        UserManagementDetailComponent,
        UserManagementUpdateComponent,
        NotificationMgmtComponent,
        NotificationMgmtDetailComponent,
        NotificationMgmtUpdateComponent,
        LogsComponent,
        JhiConfigurationComponent,
        JhiHealthCheckComponent,
        JhiHealthModalComponent,
        JhiTrackerComponent,
        JhiMetricsMonitoringComponent,
        AdminFeatureToggleComponent,
    ],
    entryComponents: [JhiHealthModalComponent],
})
export class ArtemisAdminModule {}

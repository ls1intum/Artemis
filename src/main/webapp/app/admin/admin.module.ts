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
    NotificationMgmtDeleteDialogComponent,
    NotificationMgmtDetailComponent,
    NotificationMgmtUpdateComponent,
    UserManagementComponent,
    UserManagementDeleteDialogComponent,
    UserManagementDetailComponent,
    UserManagementUpdateComponent,
} from './';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';

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
        UserManagementDeleteDialogComponent,
        NotificationMgmtComponent,
        NotificationMgmtDetailComponent,
        NotificationMgmtDeleteDialogComponent,
        NotificationMgmtUpdateComponent,
        LogsComponent,
        JhiConfigurationComponent,
        JhiHealthCheckComponent,
        JhiHealthModalComponent,
        JhiTrackerComponent,
        JhiMetricsMonitoringComponent,
    ],
    entryComponents: [UserManagementDeleteDialogComponent, NotificationMgmtDeleteDialogComponent, JhiHealthModalComponent],
})
export class ArtemisAdminModule {}

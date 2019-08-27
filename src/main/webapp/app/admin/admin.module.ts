import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
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
    adminState,
} from './';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';

/* jhipster-needle-add-admin-module-import - JHipster will add admin modules imports here */

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(adminState),
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
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
    entryComponents: [UserManagementDeleteDialogComponent, NotificationMgmtDeleteDialogComponent, JhiHealthModalComponent],
})
export class ArtemisAdminModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}

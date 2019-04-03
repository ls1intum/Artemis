import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import { ArTEMiSSharedModule } from 'app/shared';
import {
    adminState,
    AuditsComponent,
    JhiConfigurationComponent,
    JhiHealthCheckComponent,
    JhiHealthModalComponent,
    JhiMetricsMonitoringComponent,
    JhiTrackerComponent,
    LogsComponent,
    UserMgmtComponent,
    UserMgmtDeleteDialogComponent,
    UserMgmtDetailComponent,
    UserMgmtUpdateComponent,
    NotificationMgmtComponent,
    NotificationMgmtDetailComponent,
    NotificationMgmtUpdateComponent,
    NotificationMgmtDeleteDialogComponent
} from './';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';

/* jhipster-needle-add-admin-module-import - JHipster will add admin modules imports here */

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(adminState),
        FormDateTimePickerModule
        /* jhipster-needle-add-admin-module - JHipster will add admin modules here */
    ],
    declarations: [
        AuditsComponent,
        UserMgmtComponent,
        UserMgmtDetailComponent,
        UserMgmtUpdateComponent,
        UserMgmtDeleteDialogComponent,
        NotificationMgmtComponent,
        NotificationMgmtDetailComponent,
        NotificationMgmtDeleteDialogComponent,
        NotificationMgmtUpdateComponent,
        LogsComponent,
        JhiConfigurationComponent,
        JhiHealthCheckComponent,
        JhiHealthModalComponent,
        JhiTrackerComponent,
        JhiMetricsMonitoringComponent
    ],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
    entryComponents: [UserMgmtDeleteDialogComponent, NotificationMgmtDeleteDialogComponent, JhiHealthModalComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSAdminModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}

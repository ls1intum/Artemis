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
    JhiMetricsMonitoringModalComponent,
    JhiTrackerComponent,
    LogsComponent,
    UserMgmtComponent,
    UserMgmtDeleteDialogComponent,
    UserMgmtDetailComponent,
    UserMgmtUpdateComponent
} from './';

/* jhipster-needle-add-admin-module-import - JHipster will add admin modules imports here */

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(adminState)
        /* jhipster-needle-add-admin-module - JHipster will add admin modules here */
    ],
    declarations: [
        AuditsComponent,
        UserMgmtComponent,
        UserMgmtDetailComponent,
        UserMgmtUpdateComponent,
        UserMgmtDeleteDialogComponent,
        LogsComponent,
        JhiConfigurationComponent,
        JhiHealthCheckComponent,
        JhiHealthModalComponent,
        JhiTrackerComponent,
        JhiMetricsMonitoringComponent,
        JhiMetricsMonitoringModalComponent
    ],
    entryComponents: [UserMgmtDeleteDialogComponent, JhiHealthModalComponent, JhiMetricsMonitoringModalComponent],
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

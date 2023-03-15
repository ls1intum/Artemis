import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';

import { adminState } from './admin.route';
import { OrganizationManagementDetailComponent } from './organization-management/organization-management-detail.component';
import { OrganizationManagementUpdateComponent } from './organization-management/organization-management-update.component';
import { OrganizationManagementComponent } from './organization-management/organization-management.component';
import { UpcomingExamsAndExercisesComponent } from './upcoming-exams-and-exercises/upcoming-exams-and-exercises.component';
import { AuditsComponent } from 'app/admin/audits/audits.component';
import { ConfigurationComponent } from 'app/admin/configuration/configuration.component';
import { DocsComponent } from 'app/admin/docs/docs.component';
import { AdminFeatureToggleComponent } from 'app/admin/features/admin-feature-toggle.component';
import { HealthModalComponent } from 'app/admin/health/health-modal.component';
import { HealthComponent } from 'app/admin/health/health.component';
import { LogsComponent } from 'app/admin/logs/logs.component';
import { MetricsModule } from 'app/admin/metrics/metrics.module';
import { StatisticsComponent } from 'app/admin/statistics/statistics.component';
import { SystemNotificationManagementDetailComponent } from 'app/admin/system-notification-management/system-notification-management-detail.component';
import { SystemNotificationManagementUpdateComponent } from 'app/admin/system-notification-management/system-notification-management-update.component';
import { SystemNotificationManagementComponent } from 'app/admin/system-notification-management/system-notification-management.component';
import { UserManagementDetailComponent } from 'app/admin/user-management/user-management-detail.component';
import { UserManagementUpdateComponent } from 'app/admin/user-management/user-management-update.component';
import { UserManagementComponent } from 'app/admin/user-management/user-management.component';
import { ArtemisChartsModule } from 'app/shared/chart/artemis-charts.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const ENTITY_STATES = [...adminState];

@NgModule({
    imports: [
        RouterModule.forChild(ENTITY_STATES),
        ArtemisSharedModule,
        FormDateTimePickerModule,
        NgxDatatableModule,
        ArtemisDataTableModule,
        MetricsModule,
        ArtemisChartsModule,
        MatChipsModule,
        MatAutocompleteModule,
        MatSelectModule,
        MatFormFieldModule,
        ArtemisSharedComponentModule,
        ReactiveFormsModule,
    ],
    declarations: [
        AuditsComponent,
        UserManagementComponent,
        UserManagementDetailComponent,
        UserManagementUpdateComponent,
        SystemNotificationManagementComponent,
        SystemNotificationManagementDetailComponent,
        SystemNotificationManagementUpdateComponent,
        LogsComponent,
        DocsComponent,
        ConfigurationComponent,
        HealthComponent,
        HealthModalComponent,
        StatisticsComponent,
        AdminFeatureToggleComponent,
        UpcomingExamsAndExercisesComponent,
        OrganizationManagementComponent,
        OrganizationManagementDetailComponent,
        OrganizationManagementUpdateComponent,
    ],
})
export class ArtemisAdminModule {}

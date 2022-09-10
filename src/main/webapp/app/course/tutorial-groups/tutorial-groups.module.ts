import { NgModule } from '@angular/core';
import { TutorialGroupsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { TutorialGroupFormComponent } from './crud/tutorial-group-form/tutorial-group-form.component';
import { CreateTutorialGroupComponent } from './crud/create-tutorial-group/create-tutorial-group.component';
import { EditTutorialGroupComponent } from './crud/edit-tutorial-group/edit-tutorial-group.component';
import { TutorialGroupRowButtonsComponent } from './tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { RegisteredStudentsComponent } from './registered-students/registered-students.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisCourseGroupModule } from 'app/shared/course-group/course-group.module';
import { SchedulePickerComponent } from './crud/schedule-picker/schedule-picker/schedule-picker.component';
import { SessionManagementComponent } from './session-management/session-management.component';
import { CalendarModule } from 'angular-calendar';
import { ScheduleFormComponent } from './crud/tutorial-group-form/schedule-form/schedule-form.component';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TutorialGroupsConfigurationFormComponent } from './tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { CreateTutorialGroupsConfigurationComponent } from './tutorial-groups-configuration/crud/create-tutorial-groups-configuration/create-tutorial-groups-configuration/create-tutorial-groups-configuration.component';
import { EditTutorialGroupsConfigurationComponent } from './tutorial-groups-configuration/crud/edit-tutorial-groups-configuration/edit-tutorial-groups-configuration.component';
import { TutorialGroupSessionFormComponent } from './tutorial-groups-session/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { CreateTutorialGroupSessionComponent } from './tutorial-groups-session/crud/create-tutorial-group-session/create-tutorial-group-session.component';
import { CancellationModalComponent } from './session-management/cancellation-modal/cancellation-modal.component';
import { TutorialGroupFreePeriodsComponent } from './tutorial-free-periods/tutorial-group-free-periods.component';
import { TutorialGroupFreePeriodForm } from './tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { CreateTutorialGroupFreePeriodComponent } from './tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { EditTutorialGroupSessionComponent } from './tutorial-groups-session/crud/edit-tutorial-group-session/edit-tutorial-group-session.component';
import { TutorialGroupSessionRowButtonsComponent } from './session-management/tutorial-group-session-row-buttons/tutorial-group-session-row-buttons.component';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule, ArtemisDataTableModule, NgxDatatableModule, ArtemisCourseGroupModule, ArtemisMarkdownModule, CalendarModule, OwlDateTimeModule],
    declarations: [
        TutorialGroupsManagementComponent,
        TutorialGroupFormComponent,
        CreateTutorialGroupComponent,
        EditTutorialGroupComponent,
        TutorialGroupRowButtonsComponent,
        RegisteredStudentsComponent,
        SchedulePickerComponent,
        SessionManagementComponent,
        ScheduleFormComponent,
        TutorialGroupsConfigurationFormComponent,
        CreateTutorialGroupsConfigurationComponent,
        EditTutorialGroupsConfigurationComponent,
        TutorialGroupSessionFormComponent,
        CreateTutorialGroupSessionComponent,
        CancellationModalComponent,
        TutorialGroupFreePeriodsComponent,
        TutorialGroupFreePeriodForm,
        CreateTutorialGroupFreePeriodComponent,
        EditTutorialGroupSessionComponent,
        TutorialGroupSessionRowButtonsComponent,
    ],
})
export class ArtemisTutorialGroupsModule {}

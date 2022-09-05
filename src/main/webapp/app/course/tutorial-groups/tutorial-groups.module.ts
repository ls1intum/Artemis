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
import { ScheduleManagementComponent } from './schedule-management/schedule-management.component';
import { CalendarModule } from 'angular-calendar';
import { ScheduleFormComponent } from './crud/tutorial-group-form/schedule-form/schedule-form.component';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TutorialGroupsConfigurationFormComponent } from './tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { CreateTutorialGroupsConfigurationComponent } from './tutorial-groups-configuration/crud/create-tutorial-groups-configuration/create-tutorial-groups-configuration/create-tutorial-groups-configuration.component';
import { EditTutorialGroupsConfigurationComponent } from './tutorial-groups-configuration/crud/edit-tutorial-groups-configuration/edit-tutorial-groups-configuration.component';
import { TutorialGroupSessionFormComponent } from './tutorial-groups-session/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { CreateTutorialGroupSessionComponent } from './tutorial-groups-session/crud/create-tutorial-group-session/create-tutorial-group-session.component';
import { CancellationModalComponent } from './schedule-management/cancellation-modal/cancellation-modal.component';
import { TutorialFreeDaysComponent } from './tutorial-free-days/tutorial-free-days.component';
import { TutorialFreeDayFormComponent } from './tutorial-free-days/crud/tutorial-free-day-form/tutorial-free-day-form.component';
import { CreateTutorialGroupFreeDayComponent } from './tutorial-free-days/crud/create-tutorial-group-free-day/create-tutorial-group-free-day.component';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule, ArtemisDataTableModule, NgxDatatableModule, ArtemisCourseGroupModule, CalendarModule, OwlDateTimeModule],
    declarations: [
        TutorialGroupsManagementComponent,
        TutorialGroupFormComponent,
        CreateTutorialGroupComponent,
        EditTutorialGroupComponent,
        TutorialGroupRowButtonsComponent,
        RegisteredStudentsComponent,
        SchedulePickerComponent,
        ScheduleManagementComponent,
        ScheduleFormComponent,
        TutorialGroupsConfigurationFormComponent,
        CreateTutorialGroupsConfigurationComponent,
        EditTutorialGroupsConfigurationComponent,
        TutorialGroupSessionFormComponent,
        CreateTutorialGroupSessionComponent,
        CancellationModalComponent,
        TutorialFreeDaysComponent,
        TutorialFreeDayFormComponent,
        CreateTutorialGroupFreeDayComponent,
    ],
})
export class ArtemisTutorialGroupsModule {}

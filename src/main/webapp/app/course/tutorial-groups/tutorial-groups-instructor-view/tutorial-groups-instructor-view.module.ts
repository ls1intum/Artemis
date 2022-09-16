// tslint:disable:max-line-length
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { ArtemisCourseGroupModule } from 'app/shared/course-group/course-group.module';
import { TutorialGroupsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { TutorialGroupFormComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { CreateTutorialGroupComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/create-tutorial-group/create-tutorial-group.component';
import { EditTutorialGroupComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component';
import { TutorialGroupRowButtonsComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { RegisteredStudentsComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/registered-students/registered-students.component';
import { TutorialGroupSessionsManagement } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-sessions-management.component';
import { ScheduleFormComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/tutorial-group-form/schedule-form/schedule-form.component';
import { TutorialGroupsConfigurationFormComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { CreateTutorialGroupsConfigurationComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups-configuration/crud/create-tutorial-groups-configuration/create-tutorial-groups-configuration/create-tutorial-groups-configuration.component';
import { EditTutorialGroupsConfigurationComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups-configuration/crud/edit-tutorial-groups-configuration/edit-tutorial-groups-configuration.component';
import { TutorialGroupSessionFormComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { CreateTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/crud/create-tutorial-group-session/create-tutorial-group-session.component';
import { CancellationModalComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/tutorial-group-sessions-management/cancellation-modal/cancellation-modal.component';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';
import { TutorialGroupFreePeriodForm } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { CreateTutorialGroupFreePeriodComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { RouterModule } from '@angular/router';
import { tutorialGroupInstructorViewRoutes } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups-instructor-view.route';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { CalendarModule } from 'angular-calendar';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { EditTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/crud/edit-tutorial-group-session/edit-tutorial-group-session.component';
import { TutorialGroupSessionRowButtonsComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-session-row-buttons/tutorial-group-session-row-buttons.component';
import { NgModule } from '@angular/core';

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(tutorialGroupInstructorViewRoutes),
        ArtemisDataTableModule,
        NgxDatatableModule,
        ArtemisCourseGroupModule,
        ArtemisMarkdownModule,
        CalendarModule,
        OwlDateTimeModule,
    ],
    declarations: [
        TutorialGroupsManagementComponent,
        TutorialGroupFormComponent,
        CreateTutorialGroupComponent,
        EditTutorialGroupComponent,
        TutorialGroupRowButtonsComponent,
        RegisteredStudentsComponent,
        TutorialGroupSessionsManagement,
        ScheduleFormComponent,
        TutorialGroupsConfigurationFormComponent,
        CreateTutorialGroupsConfigurationComponent,
        EditTutorialGroupsConfigurationComponent,
        TutorialGroupSessionFormComponent,
        CreateTutorialGroupSessionComponent,
        CancellationModalComponent,
        TutorialGroupFreePeriodsManagementComponent,
        TutorialGroupFreePeriodForm,
        CreateTutorialGroupFreePeriodComponent,
        EditTutorialGroupSessionComponent,
        TutorialGroupSessionRowButtonsComponent,
    ],
})
export class ArtemisTutorialGroupsInstructorViewModule {}

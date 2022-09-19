// tslint:disable:max-line-length
import { RegisteredStudentsComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/registered-students/registered-students.component';
import { TutorialGroupFreePeriodForm } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { TutorialGroupSessionsManagement } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-sessions-management.component';
import { TutorialGroupsConfigurationFormComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { ArtemisCourseGroupModule } from 'app/shared/course-group/course-group.module';
import { EditTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/crud/edit-tutorial-group-session/edit-tutorial-group-session.component';
import { CreateTutorialGroupsConfigurationComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups-configuration/crud/create-tutorial-groups-configuration/create-tutorial-groups-configuration/create-tutorial-groups-configuration.component';
import { TutorialGroupFormComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { CancellationModalComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/tutorial-group-sessions-management/cancellation-modal/cancellation-modal.component';
import { TutorialGroupSessionRowButtonsComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-session-row-buttons/tutorial-group-session-row-buttons.component';
import { TutorialGroupsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { EditTutorialGroupComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component';
import { CreateTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/crud/create-tutorial-group-session/create-tutorial-group-session.component';
import { CreateTutorialGroupComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/create-tutorial-group/create-tutorial-group.component';
import { tutorialGroupInstructorViewRoutes } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups-instructor-view.route';
import { TutorialGroupRowButtonsComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { ScheduleFormComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/tutorial-group-form/schedule-form/schedule-form.component';
import { CreateTutorialGroupFreePeriodComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { TutorialGroupSessionFormComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';
import { EditTutorialGroupsConfigurationComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups-configuration/crud/edit-tutorial-groups-configuration/edit-tutorial-groups-configuration.component';
import { RouterModule } from '@angular/router';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgModule } from '@angular/core';
import { TutorialGroupFreePeriodRowButtonsComponent } from './tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-period-row-buttons/tutorial-group-free-period-row-buttons.component';
import { EditTutorialGroupFreePeriodComponent } from './tutorial-free-periods/crud/edit-tutorial-group-free-period/edit-tutorial-group-free-period.component';
import { TutorialGroupDetailComponent } from './tutorial-groups/detail/tutorial-group-detail.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(tutorialGroupInstructorViewRoutes),
        ArtemisDataTableModule,
        NgxDatatableModule,
        ArtemisCourseGroupModule,
        ArtemisMarkdownModule,
        OwlDateTimeModule,
    ],
    declarations: [
        TutorialGroupsManagementComponent,
        TutorialGroupFormComponent,
        CreateTutorialGroupComponent,
        EditTutorialGroupComponent,
        TutorialGroupRowButtonsComponent,
        RegisteredStudentsComponent,
        TutorialGroupDetailComponent,
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
        TutorialGroupFreePeriodRowButtonsComponent,
        EditTutorialGroupFreePeriodComponent,
    ],
})
export class ArtemisTutorialGroupsInstructorViewModule {}

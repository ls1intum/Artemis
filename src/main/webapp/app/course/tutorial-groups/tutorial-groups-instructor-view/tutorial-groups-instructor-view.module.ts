import { NgModule } from '@angular/core';
import { TutorialGroupsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { TutorialGroupFormComponent } from './tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { CreateTutorialGroupComponent } from './tutorial-groups/crud/create-tutorial-group/create-tutorial-group.component';
import { EditTutorialGroupComponent } from './tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { RegisteredStudentsComponent } from './registered-students/registered-students.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisCourseGroupModule } from 'app/shared/course-group/course-group.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { tutorialGroupInstructorViewRoutes } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups-instructor-view.route';
import { ArtemisTutorialGroupsSharedModule } from 'app/course/tutorial-groups/shared/tutorial-groups-shared.module';
import { TutorialGroupRowButtonsComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { TutorialGroupManagementDetailComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/detail/tutorial-group-management-detail.component';

@NgModule({
    imports: [
        RouterModule.forChild(tutorialGroupInstructorViewRoutes),
        ArtemisSharedModule,
        ArtemisDataTableModule,
        NgxDatatableModule,
        ArtemisCourseGroupModule,
        ArtemisMarkdownModule,
        ArtemisTutorialGroupsSharedModule,
    ],
    declarations: [
        TutorialGroupsManagementComponent,
        TutorialGroupFormComponent,
        CreateTutorialGroupComponent,
        EditTutorialGroupComponent,
        RegisteredStudentsComponent,
        TutorialGroupRowButtonsComponent,
        TutorialGroupManagementDetailComponent,
    ],
})
export class ArtemisTutorialGroupsInstructorViewModule {}

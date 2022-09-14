import { NgModule } from '@angular/core';
import { TutorialGroupsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { TutorialGroupFormComponent } from './tutorial-groups-instructor-view/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { CreateTutorialGroupComponent } from './tutorial-groups-instructor-view/tutorial-groups/crud/create-tutorial-group/create-tutorial-group.component';
import { EditTutorialGroupComponent } from './tutorial-groups-instructor-view/tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component';
import { TutorialGroupRowButtonsComponent } from './tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { RegisteredStudentsComponent } from './tutorial-groups-instructor-view/registered-students/registered-students.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisCourseGroupModule } from 'app/shared/course-group/course-group.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule, ArtemisDataTableModule, NgxDatatableModule, ArtemisCourseGroupModule, ArtemisMarkdownModule],
    declarations: [
        TutorialGroupsManagementComponent,
        TutorialGroupFormComponent,
        CreateTutorialGroupComponent,
        EditTutorialGroupComponent,
        TutorialGroupRowButtonsComponent,
        RegisteredStudentsComponent,
    ],
})
export class ArtemisTutorialGroupsInstructorViewModule {}

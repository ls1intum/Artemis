import { NgModule } from '@angular/core';
import { TutorialGroupsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { TutorialGroupFormComponent } from './tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { CreateTutorialGroupComponent } from './tutorial-groups/crud/create-tutorial-group/create-tutorial-group.component';
import { EditTutorialGroupComponent } from './tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component';
import { TutorialGroupRowButtonsComponent } from './tutorial-groups/tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { RegisteredStudentsComponent } from './registered-students/registered-students.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisCourseGroupModule } from 'app/shared/course-group/course-group.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { TutorialGroupDetailComponent } from './tutorial-groups/detail/tutorial-group-detail.component';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule, ArtemisDataTableModule, NgxDatatableModule, ArtemisCourseGroupModule, ArtemisMarkdownModule],
    declarations: [
        TutorialGroupsManagementComponent,
        TutorialGroupFormComponent,
        CreateTutorialGroupComponent,
        EditTutorialGroupComponent,
        TutorialGroupRowButtonsComponent,
        RegisteredStudentsComponent,
        TutorialGroupDetailComponent,
    ],
})
export class ArtemisTutorialGroupsInstructorViewModule {}

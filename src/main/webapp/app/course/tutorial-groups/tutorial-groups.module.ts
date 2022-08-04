import { NgModule } from '@angular/core';
import { TutorialGroupsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { TutorialGroupFormComponent } from './crud/tutorial-group-form/tutorial-group-form.component';
import { CreateTutorialGroupComponent } from './crud/create-tutorial-group/create-tutorial-group.component';
import { EditTutorialGroupComponent } from './crud/edit-tutorial-group/edit-tutorial-group.component';
import { TutorialGroupRowButtonsComponent } from './tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule],
    declarations: [TutorialGroupsManagementComponent, TutorialGroupFormComponent, CreateTutorialGroupComponent, EditTutorialGroupComponent, TutorialGroupRowButtonsComponent],
})
export class ArtemisTutorialGroupsModule {}

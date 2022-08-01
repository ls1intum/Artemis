import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TutorialGroupsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TutorialGroupFormComponent } from './tutorial-group-form/tutorial-group-form.component';
import { CreateTutorialGroupComponent } from './create-tutorial-group/create-tutorial-group.component';
import { EditTutorialGroupComponent } from './edit-tutorial-group/edit-tutorial-group.component';
import { TutorialGroupRowButtonsComponent } from './tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule],
    declarations: [TutorialGroupsManagementComponent, TutorialGroupFormComponent, CreateTutorialGroupComponent, EditTutorialGroupComponent, TutorialGroupRowButtonsComponent],
})
export class ArtemisTutorialGroupsModule {}

import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TutorialGroupsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule],
    declarations: [TutorialGroupsManagementComponent],
})
export class ArtemisTutorialGroupsModule {}

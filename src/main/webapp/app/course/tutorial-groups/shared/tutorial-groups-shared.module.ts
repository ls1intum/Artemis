import { NgModule } from '@angular/core';
import { TutorialGroupsTableComponent } from 'app/course/tutorial-groups/shared/tutorial-groups-table/tutorial-groups-table.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { TutorialGroupDetailComponent } from './tutorial-group-detail/tutorial-group-detail.component';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule],
    declarations: [TutorialGroupsTableComponent, TutorialGroupDetailComponent],
    exports: [TutorialGroupsTableComponent, TutorialGroupDetailComponent],
})
export class ArtemisTutorialGroupsSharedModule {}

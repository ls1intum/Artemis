import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CourseTutorialGroupsComponent } from 'app/overview/course-tutorial-groups/course-tutorial-groups.component';
import { routes } from './course-tutorial-groups.route';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseTutorialGroupCardComponent } from './course-tutorial-group-card/course-tutorial-group-card.component';
import { CourseTutorialGroupsOverviewComponent } from './course-tutorial-groups-overview/course-tutorial-groups-overview.component';
import { ArtemisTutorialGroupsSharedModule } from 'app/course/tutorial-groups/shared/tutorial-groups-shared.module';
import { CourseTutorialGroupsRegisteredComponent } from './course-tutorial-groups-registered/course-tutorial-groups-registered.component';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { CourseTutorialGroupSidePanelComponent } from './course-tutorial-group-side-panel/course-tutorial-group-side-panel.component';

@NgModule({
    imports: [RouterModule.forChild(routes), ArtemisSharedModule, ArtemisTutorialGroupsSharedModule, ArtemisSidePanelModule],
    declarations: [
        CourseTutorialGroupsComponent,
        CourseTutorialGroupCardComponent,
        CourseTutorialGroupsOverviewComponent,
        CourseTutorialGroupsRegisteredComponent,
        CourseTutorialGroupSidePanelComponent,
    ],
})
export class CourseTutorialGroupsModule {}

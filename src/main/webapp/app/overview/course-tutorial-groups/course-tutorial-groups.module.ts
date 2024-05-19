import { NgModule } from '@angular/core';
import { CourseTutorialGroupsComponent } from 'app/overview/course-tutorial-groups/course-tutorial-groups.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseTutorialGroupCardComponent } from './course-tutorial-group-card/course-tutorial-group-card.component';
import { ArtemisTutorialGroupsSharedModule } from 'app/course/tutorial-groups/shared/tutorial-groups-shared.module';
import { ArtemisSidebarModule } from 'app/shared/sidebar/sidebar.module';
import { ArtemisAppRoutingModule } from 'app/app-routing.module';

@NgModule({
    imports: [ArtemisAppRoutingModule, ArtemisSharedModule, ArtemisTutorialGroupsSharedModule, ArtemisSidebarModule],
    declarations: [CourseTutorialGroupsComponent, CourseTutorialGroupCardComponent],
})
export class CourseTutorialGroupsModule {}

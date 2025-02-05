import { NgModule } from '@angular/core';
import { CourseTutorialGroupsComponent } from 'app/overview/course-tutorial-groups/course-tutorial-groups.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseTutorialGroupCardComponent } from './course-tutorial-group-card/course-tutorial-group-card.component';
import { ArtemisTutorialGroupsSharedModule } from 'app/course/tutorial-groups/shared/tutorial-groups-shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisTutorialGroupsSharedModule, CourseTutorialGroupsComponent, CourseTutorialGroupCardComponent],
})
export class CourseTutorialGroupsModule {}

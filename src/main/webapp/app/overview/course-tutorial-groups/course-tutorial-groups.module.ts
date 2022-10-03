import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CourseTutorialGroupsComponent } from 'app/overview/course-tutorial-groups/course-tutorial-groups.component';
import { routes } from './course-tutorial-groups.route';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseTutorialGroupCardComponent } from './course-tutorial-group-card/course-tutorial-group-card.component';

@NgModule({
    imports: [RouterModule.forChild(routes), ArtemisSharedModule],
    declarations: [CourseTutorialGroupsComponent, CourseTutorialGroupCardComponent],
})
export class CourseTutorialGroupsModule {}

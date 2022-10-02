import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CourseTutorialGroupsComponent } from 'app/overview/course-tutorial-groups/course-tutorial-groups.component';
import { routes } from './course-tutorial-groups.route';

@NgModule({
    imports: [RouterModule.forChild(routes)],
    declarations: [CourseTutorialGroupsComponent],
})
export class CourseTutorialGroupsModule {}

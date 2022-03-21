import { MetisModule } from 'app/shared/metis/metis.module';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CourseDiscussionComponent } from 'app/overview/course-discussion/course-discussion.component';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        data: {
            pageTitle: 'artemisApp.metis.discussion.label',
        },
        component: CourseDiscussionComponent,
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), MetisModule],
    declarations: [CourseDiscussionComponent],
})
export class CourseDiscussionModule {}

import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule, Routes } from '@angular/router';
import { MetisModule } from 'app/shared/metis/metis.module';
import { CourseDiscussionComponent } from 'app/overview/course-discussion/course-discussion.component';
import { TagInputModule } from 'ngx-chips';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        component: CourseDiscussionComponent,
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), MetisModule, ArtemisSharedModule],
    declarations: [CourseDiscussionComponent],
    exports: [CourseDiscussionComponent],
})
export class CourseDiscussionModule {}

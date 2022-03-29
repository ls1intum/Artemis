import { MetisModule } from 'app/shared/metis/metis.module';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { InfiniteScrollModule } from 'ngx-infinite-scroll';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
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
    imports: [RouterModule.forChild(routes), MetisModule, ArtemisSharedComponentModule, InfiniteScrollModule],
    declarations: [CourseDiscussionComponent],
})
export class CourseDiscussionModule {}

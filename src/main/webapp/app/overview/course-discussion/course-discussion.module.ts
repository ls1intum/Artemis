import { MetisModule } from 'app/shared/metis/metis.module';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CourseDiscussionComponent } from 'app/overview/course-discussion/course-discussion.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { InfiniteScrollModule } from 'ngx-infinite-scroll';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        data: {
            pageTitle: 'artemisApp.metis.communication.label',
        },
        component: CourseDiscussionComponent,
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), MetisModule, ArtemisSharedModule, ArtemisSharedCommonModule, ArtemisSharedComponentModule, InfiniteScrollModule],
    declarations: [CourseDiscussionComponent],
})
export class CourseDiscussionModule {}

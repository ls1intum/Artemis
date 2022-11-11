import { MetisModule } from 'app/shared/metis/metis.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseDiscussionComponent } from 'app/overview/course-discussion/course-discussion.component';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { AgVirtualScrollModule } from 'ag-virtual-scroll';

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
    imports: [RouterModule.forChild(routes), MetisModule, ArtemisSharedModule, ArtemisSharedComponentModule, AgVirtualScrollModule],
    declarations: [CourseDiscussionComponent],
    exports: [CourseDiscussionComponent],
})
export class CourseDiscussionModule {}

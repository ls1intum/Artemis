import { MetisModule } from 'app/shared/metis/metis.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseDiscussionComponent } from 'app/overview/course-discussion/course-discussion.component';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { VirtualScrollModule } from 'app/shared/virtual-scroll/virtual-scroll.module';
import { Authority } from 'app/shared/constants/authority.constants';
import { MatSelectModule } from '@angular/material/select';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        data: {
            pageTitle: 'artemisApp.metis.communication.label',
            authorities: [Authority.USER],
        },
        component: CourseDiscussionComponent,
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), MetisModule, ArtemisSharedModule, ArtemisSharedComponentModule, VirtualScrollModule, MatSelectModule],
    declarations: [CourseDiscussionComponent],
    exports: [CourseDiscussionComponent],
})
export class CourseDiscussionModule {}

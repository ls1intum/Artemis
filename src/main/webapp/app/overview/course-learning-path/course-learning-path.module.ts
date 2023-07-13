import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ArtemisLearningPathsModule } from 'app/course/learning-paths/learning-paths.module';
import { CourseLearningPathComponent } from 'app/overview/course-learning-path/course-learning-path.component';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.learningPath',
        },
        component: CourseLearningPathComponent,
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisLearningPathsModule],
    declarations: [CourseLearningPathComponent],
    exports: [CourseLearningPathComponent],
})
export class CourseLearningPathModule {}

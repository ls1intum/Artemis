import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExampleModelingSolutionComponent } from './example-modeling-solution.component';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: ':courseId/modeling-exercises/:exerciseId/example-solution',
        component: ExampleModelingSolutionComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.modelingExercise.exampleSolution',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisExampleModelingSolutionRoutingModule {}

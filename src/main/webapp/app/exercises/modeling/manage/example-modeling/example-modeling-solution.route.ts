import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExampleModelingSolutionComponent } from './example-modeling-solution.component';

const routes: Routes = [
    {
        path: ':courseId/modeling-exercises/:exerciseId/example-solution',
        component: ExampleModelingSolutionComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
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

import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { NgModule } from '@angular/core';
import { ProgrammingExerciseConfigureGradingComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading.component';
import { CanDeactivateGuard } from 'app/shared/guard/can-deactivate.guard';

const routes: Routes = [
    {
        path: 'test-cases',
        component: ProgrammingExerciseConfigureGradingComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [CanDeactivateGuard],
    },
    {
        path: 'code-analysis',
        component: ProgrammingExerciseConfigureGradingComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [CanDeactivateGuard],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisProgrammingExerciseGradingRoutingModule {}

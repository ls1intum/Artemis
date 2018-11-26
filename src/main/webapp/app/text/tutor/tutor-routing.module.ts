import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { ArTEMiSTextTutorComponent } from 'app/text/tutor/tutor.component';

const routes: Routes = [
    {
        path: 'text/:exerciseId/:submissionId/tutor',
        component: ArTEMiSTextTutorComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.textExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class ArTEMiSTextTutorRoutingModule {}

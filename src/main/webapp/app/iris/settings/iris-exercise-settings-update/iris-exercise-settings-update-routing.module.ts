import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { IrisModule } from 'app/iris/iris.module';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IrisExerciseSettingsUpdateComponent } from 'app/iris/settings/iris-exercise-settings-update/iris-exercise-settings-update.component';
import { IrisGuard } from 'app/iris/iris-guard.service';

const routes: Routes = [
    {
        path: '',
        component: IrisExerciseSettingsUpdateComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.iris.settings.title.exercise',
        },
        canActivate: [UserRouteAccessService, IrisGuard],
        canDeactivate: [PendingChangesGuard],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), IrisModule],
    exports: [RouterModule],
})
export class IrisExerciseSettingsUpdateRoutingModule {}

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { IrisGlobalSettingsUpdateComponent } from 'app/iris/settings/iris-global-settings-update/iris-global-settings-update.component';
import { IrisModule } from 'app/iris/iris.module';

const routes: Routes = [
    {
        path: '',
        component: IrisGlobalSettingsUpdateComponent,
        data: {
            authorities: [Authority.ADMIN],
            pageTitle: 'artemisApp.iris.settings.title.global',
        },
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), IrisModule],
    exports: [RouterModule],
})
export class IrisSettingsUpdateRoutingModule {}

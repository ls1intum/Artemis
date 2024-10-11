import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SharingComponent } from 'app/sharing/sharing.component';
import { featureOverviewState } from 'app/sharing/sharing.route';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const SHARING_ROUTES = [...featureOverviewState];
@NgModule({
    imports: [RouterModule.forChild(SHARING_ROUTES), ArtemisSharedModule],
    declarations: [SharingComponent],
})
export class SharingModule {}

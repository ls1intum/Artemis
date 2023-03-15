import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { DiscussionSectionComponent } from 'app/overview/discussion-section/discussion-section.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { MetisModule } from 'app/shared/metis/metis.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        component: DiscussionSectionComponent,
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), MetisModule, ArtemisSharedModule, ArtemisSidePanelModule, ArtemisSharedComponentModule],
    declarations: [DiscussionSectionComponent],
    exports: [DiscussionSectionComponent],
})
export class DiscussionSectionModule {}

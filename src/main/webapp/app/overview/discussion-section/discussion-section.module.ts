import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { DiscussionSectionComponent } from 'app/overview/discussion-section/discussion-section.component';
import { RouterModule, Routes } from '@angular/router';
import { MetisModule } from 'app/shared/metis/metis.module';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        component: DiscussionSectionComponent,
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), MetisModule, ArtemisSharedModule, ArtemisSidePanelModule],
    declarations: [DiscussionSectionComponent],
    exports: [DiscussionSectionComponent],
})
export class DiscussionSectionModule {}

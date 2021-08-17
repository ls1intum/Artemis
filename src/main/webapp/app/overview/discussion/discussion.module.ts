import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { DiscussionComponent } from 'app/overview/discussion/discussion.component';
import { RouterModule, Routes } from '@angular/router';
import { MetisModule } from 'app/shared/metis/metis.module';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        component: DiscussionComponent,
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), MetisModule, ArtemisSharedModule, ArtemisSidePanelModule],
    declarations: [DiscussionComponent],
    exports: [DiscussionComponent],
})
export class DiscussionModule {}

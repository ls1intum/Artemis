import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { TutorialGroupDetailComponent } from './tutorial-group-detail/tutorial-group-detail.component';
import { TutorialGroupsTableComponent } from './tutorial-groups-table/tutorial-groups-table.component';
import { TutorialGroupSessionsTableComponent } from './tutorial-group-sessions-table/tutorial-group-sessions-table.component';
import { TutorialGroupSessionRowComponent } from 'app/course/tutorial-groups/shared/tutorial-group-sessions-table/tutorial-group-session-row/tutorial-group-session-row.component';
import { TutorialGroupFreeDaysOverviewComponent } from './tutorial-group-free-days-overview/tutorial-group-free-days-overview.component';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule, ArtemisSidePanelModule],
    declarations: [
        TutorialGroupsTableComponent,
        TutorialGroupDetailComponent,
        TutorialGroupSessionsTableComponent,
        TutorialGroupSessionRowComponent,
        TutorialGroupFreeDaysOverviewComponent,
    ],
    exports: [TutorialGroupsTableComponent, TutorialGroupSessionsTableComponent, TutorialGroupDetailComponent, TutorialGroupFreeDaysOverviewComponent],
})
export class ArtemisTutorialGroupsSharedModule {}

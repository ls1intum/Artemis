import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { TutorialGroupDetailComponent } from './tutorial-group-detail/tutorial-group-detail.component';
import { TutorialGroupsTableComponent } from './tutorial-groups-table/tutorial-groups-table.component';
import { TutorialGroupSessionsTableComponent } from './tutorial-group-sessions-table/tutorial-group-sessions-table.component';
import { TutorialGroupSessionRowComponent } from 'app/course/tutorial-groups/shared/tutorial-group-sessions-table/tutorial-group-session-row/tutorial-group-session-row.component';
import { TutorialGroupFreeDaysOverviewComponent } from './tutorial-group-free-days-overview/tutorial-group-free-days-overview.component';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { TutorialGroupRowComponent } from './tutorial-groups-table/tutorial-group-row/tutorial-group-row.component';
import { VerticalProgressBarModule } from 'app/shared/vertical-progress-bar/vertical-progress-bar.module';
import { TutorialGroupUtilizationIndicatorComponent } from './tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule, ArtemisSidePanelModule, VerticalProgressBarModule],
    declarations: [
        TutorialGroupsTableComponent,
        TutorialGroupDetailComponent,
        TutorialGroupSessionsTableComponent,
        TutorialGroupSessionRowComponent,
        TutorialGroupFreeDaysOverviewComponent,
        TutorialGroupRowComponent,
        TutorialGroupUtilizationIndicatorComponent,
    ],
    exports: [TutorialGroupsTableComponent, TutorialGroupSessionsTableComponent, TutorialGroupDetailComponent, TutorialGroupFreeDaysOverviewComponent],
})
export class ArtemisTutorialGroupsSharedModule {}

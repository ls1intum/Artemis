import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TutorialGroupSessionRowComponent } from 'app/course/tutorial-groups/shared/tutorial-group-sessions-table/tutorial-group-session-row/tutorial-group-session-row.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { VerticalProgressBarModule } from 'app/shared/vertical-progress-bar/vertical-progress-bar.module';
import { TutorialGroupDetailComponent } from './tutorial-group-detail/tutorial-group-detail.component';
import { TutorialGroupFreeDaysOverviewComponent } from './tutorial-group-free-days-overview/tutorial-group-free-days-overview.component';
import { TutorialGroupSessionsTableComponent } from './tutorial-group-sessions-table/tutorial-group-sessions-table.component';
import { TutorialGroupUtilizationIndicatorComponent } from './tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';
import { TutorialGroupRowComponent } from './tutorial-groups-table/tutorial-group-row/tutorial-group-row.component';
import { TutorialGroupsTableComponent } from './tutorial-groups-table/tutorial-groups-table.component';

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

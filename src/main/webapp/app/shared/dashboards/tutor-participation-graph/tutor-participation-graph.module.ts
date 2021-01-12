import { NgModule } from '@angular/core';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { TutorParticipationGraphComponent } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ProgressBarComponent, TutorParticipationGraphComponent],
    exports: [ProgressBarComponent, TutorParticipationGraphComponent],
})
export class ArtemisTutorParticipationGraphModule {}

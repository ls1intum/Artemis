import { NgModule } from '@angular/core';
import { TutorParticipationTableComponent } from 'app/shared/dashboards/tutor-participation-table/tutor-participation-table.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [TutorParticipationTableComponent],
    exports: [TutorParticipationTableComponent],
})
export class ArtemisTutorParticipationTableModule {}

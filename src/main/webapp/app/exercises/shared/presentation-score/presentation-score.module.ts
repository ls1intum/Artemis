import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PresentationScoreComponent } from 'app/exercises/shared/presentation-score/presentation-score.component';

@NgModule({
    imports: [ArtemisSharedModule, PresentationScoreComponent],
    exports: [PresentationScoreComponent],
})
export class ArtemisPresentationScoreModule {}

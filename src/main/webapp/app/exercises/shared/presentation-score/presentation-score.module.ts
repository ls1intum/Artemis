import { NgModule } from '@angular/core';

import { PresentationScoreComponent } from 'app/exercises/shared/presentation-score/presentation-score.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [PresentationScoreComponent],
    exports: [PresentationScoreComponent],
})
export class ArtemisPresentationScoreModule {}

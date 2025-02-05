import { NgModule } from '@angular/core';

import { PresentationScoreComponent } from 'app/exercises/shared/presentation-score/presentation-score.component';

@NgModule({
    imports: [PresentationScoreComponent],
    exports: [PresentationScoreComponent],
})
export class ArtemisPresentationScoreModule {}

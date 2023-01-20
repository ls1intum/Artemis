import { NgModule } from '@angular/core';
import { ResultBadgesComponent } from 'app/exercises/shared/result-badges/result-badges.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ResultBadgesComponent],
    exports: [ResultBadgesComponent],
})
export class ArtemisResultBadgesModule {}

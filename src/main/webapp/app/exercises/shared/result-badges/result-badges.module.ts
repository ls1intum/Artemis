import { NgModule } from '@angular/core';
import { ResultBadgeComponent } from 'app/exercises/shared/result-badges/result-badge.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ResultBadgeComponent],
    exports: [ResultBadgeComponent],
})
export class ArtemisResultBadgeModule {}

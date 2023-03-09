import { NgModule } from '@angular/core';

import { AlertOverlayComponent } from 'app/shared/alert/alert-overlay.component';
import { CloseCircleComponent } from 'app/shared/close-circle/close-circle.component';
import { FindLanguageFromKeyPipe } from 'app/shared/language/find-language-from-key.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDateRangePipe } from 'app/shared/pipes/artemis-date-range.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DurationPipe } from 'app/shared/pipes/duration.pipe';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';

@NgModule({
    imports: [ArtemisSharedLibsModule],
    declarations: [
        ArtemisDatePipe,
        ArtemisDateRangePipe,
        FindLanguageFromKeyPipe,
        AlertOverlayComponent,
        TranslateDirective,
        SortByDirective,
        SortDirective,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
        ArtemisDurationFromSecondsPipe,
        DurationPipe,
        CloseCircleComponent,
    ],
    exports: [
        ArtemisSharedLibsModule,
        ArtemisDatePipe,
        ArtemisDateRangePipe,
        FindLanguageFromKeyPipe,
        AlertOverlayComponent,
        TranslateDirective,
        SortByDirective,
        SortDirective,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
        ArtemisDurationFromSecondsPipe,
        DurationPipe,
        CloseCircleComponent,
    ],
    providers: [ArtemisDatePipe, ArtemisDateRangePipe],
})
export class ArtemisSharedCommonModule {}

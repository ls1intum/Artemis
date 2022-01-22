import { NgModule } from '@angular/core';

import { FindLanguageFromKeyPipe } from 'app/shared/language/find-language-from-key.pipe';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { AlertOverlayComponent } from 'app/shared/alert/alert-overlay.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { DurationPipe } from 'app/shared/pipes/duration.pipe';

@NgModule({
    imports: [ArtemisSharedLibsModule],
    declarations: [
        ArtemisDatePipe,
        FindLanguageFromKeyPipe,
        AlertOverlayComponent,
        TranslateDirective,
        SortByDirective,
        SortDirective,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
        ArtemisDurationFromSecondsPipe,
        DurationPipe,
    ],
    exports: [
        ArtemisSharedLibsModule,
        ArtemisDatePipe,
        FindLanguageFromKeyPipe,
        AlertOverlayComponent,
        TranslateDirective,
        SortByDirective,
        SortDirective,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
        ArtemisDurationFromSecondsPipe,
        DurationPipe,
    ],
    providers: [ArtemisDatePipe],
})
export class ArtemisSharedCommonModule {}

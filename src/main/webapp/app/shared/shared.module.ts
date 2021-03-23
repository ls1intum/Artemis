import { NgModule } from '@angular/core';
import { SlideToggleComponent } from 'app/exercises/shared/slide-toggle/slide-toggle.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { ChartComponent } from 'app/shared/chart/chart.component';
import { CircularProgressBarComponent } from 'app/shared/circular-progress-bar/circular-progress-bar.component';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { SecureLinkDirective } from 'app/shared/http/secure-link.directive';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { FindLanguageFromKeyPipe } from 'app/shared/language/find-language-from-key.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { CustomMaxDirective } from 'app/shared/validators/custom-max-validator.directive';
import { CustomMinDirective } from 'app/shared/validators/custom-min-validator.directive';
import { AdditionalFeedbackComponent } from './additional-feedback/additional-feedback.component';
import { ResizeableContainerComponent } from './resizeable-container/resizeable-container.component';
import { TranslatePipe } from '@ngx-translate/core';
import { StatisticsGraphComponent } from 'app/admin/statistics/statistics-graph.component';

@NgModule({
    imports: [ArtemisSharedLibsModule, ArtemisSharedCommonModule, ArtemisSharedPipesModule],
    declarations: [
        ArtemisDatePipe,
        CircularProgressBarComponent,
        ArtemisDurationFromSecondsPipe,
        HasAnyAuthorityDirective,
        SecuredImageComponent,
        DeleteDialogComponent,
        AdditionalFeedbackComponent,
        DeleteButtonDirective,
        ResizeableContainerComponent,
        SecureLinkDirective,
        SlideToggleComponent,
        JhiConnectionStatusComponent,
        ChartComponent,
        CustomMinDirective,
        CustomMaxDirective,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
        StatisticsGraphComponent,
    ],
    entryComponents: [DeleteDialogComponent],
    exports: [
        ArtemisDatePipe,
        ArtemisDurationFromSecondsPipe,
        ArtemisSharedLibsModule,
        CircularProgressBarComponent,
        FindLanguageFromKeyPipe,
        AlertComponent,
        AlertErrorComponent,
        AdditionalFeedbackComponent,
        HasAnyAuthorityDirective,
        ArtemisSharedCommonModule,
        ArtemisSharedPipesModule,
        SecuredImageComponent,
        DeleteButtonDirective,
        DeleteDialogComponent,
        ResizeableContainerComponent,
        SecureLinkDirective,
        SlideToggleComponent,
        JhiConnectionStatusComponent,
        ChartComponent,
        CustomMinDirective,
        CustomMaxDirective,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
        StatisticsGraphComponent,
    ],
    providers: [ArtemisDatePipe, TranslatePipe],
})
export class ArtemisSharedModule {}

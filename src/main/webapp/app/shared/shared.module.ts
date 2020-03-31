import { DatePipe } from '@angular/common';
import { NgModule } from '@angular/core';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ResizeableContainerComponent } from './resizeable-container/resizeable-container.component';
import { SecureLinkDirective } from 'app/shared/http/secure-link.directive';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { FindLanguageFromKeyPipe } from 'app/shared/language/find-language-from-key.pipe';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { SlideToggleComponent } from 'app/exercises/shared/slide-toggle/slide-toggle.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';

@NgModule({
    imports: [ArtemisSharedLibsModule, ArtemisSharedCommonModule, ArtemisSharedPipesModule],
    declarations: [
        HasAnyAuthorityDirective,
        SecuredImageComponent,
        DeleteDialogComponent,
        DeleteButtonDirective,
        ResizeableContainerComponent,
        SecureLinkDirective,
        SlideToggleComponent,
        JhiConnectionStatusComponent,
    ],
    providers: [DatePipe],
    entryComponents: [DeleteDialogComponent],
    exports: [
        ArtemisSharedLibsModule,
        FindLanguageFromKeyPipe,
        AlertComponent,
        AlertErrorComponent,
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
    ],
})
export class ArtemisSharedModule {}

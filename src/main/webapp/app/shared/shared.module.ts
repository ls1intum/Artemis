import { DatePipe } from '@angular/common';
import { NgModule } from '@angular/core';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ResizeableContainerComponent } from './resizeable-container/resizeable-container.component';
import { SecureLinkDirective } from 'app/shared/http/secure-link.directive';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { JhiAlertComponent } from 'app/shared/alert/alert.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { FindLanguageFromKeyPipe } from 'app/shared/language/find-language-from-key.pipe';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { JhiAlertErrorComponent } from 'app/shared/alert/alert-error.component';

@NgModule({
    imports: [ArtemisSharedLibsModule, ArtemisSharedCommonModule, ArtemisSharedPipesModule],
    declarations: [HasAnyAuthorityDirective, SecuredImageComponent, DeleteDialogComponent, DeleteButtonDirective, ResizeableContainerComponent, SecureLinkDirective],
    providers: [DatePipe],
    entryComponents: [DeleteDialogComponent],
    exports: [
        ArtemisSharedLibsModule,
        FindLanguageFromKeyPipe,
        JhiAlertComponent,
        JhiAlertErrorComponent,
        HasAnyAuthorityDirective,
        ArtemisSharedCommonModule,
        ArtemisSharedPipesModule,
        SecuredImageComponent,
        DeleteButtonDirective,
        DeleteDialogComponent,
        ResizeableContainerComponent,
        SecureLinkDirective,
    ],
})
export class ArtemisSharedModule {}

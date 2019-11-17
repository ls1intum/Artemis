import { DatePipe } from '@angular/common';
import { NgModule } from '@angular/core';
import { ArtemisSharedCommonModule, ArtemisSharedLibsModule, HasAnyAuthorityDirective, JhiAlertErrorComponent, JhiAlertComponent, FindLanguageFromKeyPipe } from './';
import { FileUploaderService } from './http/file-uploader.service';
import { FileService } from './http/file.service';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { CacheableImageService } from 'app/shared/image/cacheable-image.service';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { SecureLinkDirective } from 'app/shared/http/secure-link.directive';

@NgModule({
    imports: [ArtemisSharedLibsModule, ArtemisSharedCommonModule, ArtemisSharedPipesModule],
    declarations: [HasAnyAuthorityDirective, SecuredImageComponent, DeleteDialogComponent, DeleteButtonDirective, SecureLinkDirective],
    providers: [FileService, FileUploaderService, DatePipe, CacheableImageService, DeleteDialogService],
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
        SecureLinkDirective,
    ],
})
export class ArtemisSharedModule {}

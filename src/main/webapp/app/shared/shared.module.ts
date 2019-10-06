import { DatePipe } from '@angular/common';
import { NgModule } from '@angular/core';
import { NgbDateAdapter } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisSharedCommonModule, ArtemisSharedLibsModule, HasAnyAuthorityDirective } from './';
import { FileUploaderService } from './http/file-uploader.service';
import { FileService } from './http/file.service';
import { NgbDateMomentAdapter } from './util/datepicker-adapter';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { CacheableImageService } from 'app/shared/image/cacheable-image.service';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { TranslateModule } from '@ngx-translate/core';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { PresentationScoreComponent } from 'app/components/exercise/presentation-score/presentation-score.component';

@NgModule({
    imports: [ArtemisSharedLibsModule, ArtemisSharedCommonModule, ArtemisSharedPipesModule, TranslateModule],
    declarations: [HasAnyAuthorityDirective, SecuredImageComponent, DeleteDialogComponent, DeleteButtonDirective, PresentationScoreComponent],
    providers: [FileService, FileUploaderService, DatePipe, { provide: NgbDateAdapter, useClass: NgbDateMomentAdapter }, CacheableImageService, DeleteDialogService],
    entryComponents: [DeleteDialogComponent],
    exports: [ArtemisSharedCommonModule, ArtemisSharedPipesModule, HasAnyAuthorityDirective, SecuredImageComponent, TranslateModule, DeleteButtonDirective, PresentationScoreComponent],
})
export class ArtemisSharedModule {
    static forRoot() {
        return {
            ngModule: ArtemisSharedModule,
        };
    }
}

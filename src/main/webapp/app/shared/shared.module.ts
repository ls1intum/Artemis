import { DatePipe } from '@angular/common';
import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { NgbDateAdapter } from '@ng-bootstrap/ng-bootstrap';
import {
    ArtemisSharedCommonModule,
    ArtemisSharedLibsModule,
    HasAnyAuthorityDirective,
    RemoveKeysPipe,
    SafeHtmlPipe,
    SafeUrlPipe,
    KeysPipe,
    TypeCheckPipe,
    HtmlForMarkdownPipe,
    TruncatePipe,
} from './';
import { FileUploaderService } from './http/file-uploader.service';
import { FileService } from './http/file.service';
import { NgbDateMomentAdapter } from './util/datepicker-adapter';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { CacheableImageService } from 'app/shared/image/cacheable-image.service';
import { ExerciseTypePipe } from 'app/entities/exercise';
import { RemovePositiveAutomaticFeedbackPipe } from 'app/shared/pipes/remove-positive-automatic-feedback.pipe';
import { SanitizeHtmlPipe } from 'app/shared/pipes/sanitize-html.pipe';

@NgModule({
    imports: [ArtemisSharedLibsModule, ArtemisSharedCommonModule],
    declarations: [
        HasAnyAuthorityDirective,
        SafeHtmlPipe,
        SafeUrlPipe,
        RemoveKeysPipe,
        ExerciseTypePipe,
        KeysPipe,
        TypeCheckPipe,
        RemovePositiveAutomaticFeedbackPipe,
        HtmlForMarkdownPipe,
        TruncatePipe,
        SanitizeHtmlPipe,
        SecuredImageComponent,
    ],
    providers: [FileService, FileUploaderService, DatePipe, { provide: NgbDateAdapter, useClass: NgbDateMomentAdapter }, CacheableImageService],
    entryComponents: [],
    exports: [
        ArtemisSharedCommonModule,
        HasAnyAuthorityDirective,
        DatePipe,
        SafeHtmlPipe,
        SafeUrlPipe,
        RemoveKeysPipe,
        ExerciseTypePipe,
        KeysPipe,
        TypeCheckPipe,
        RemovePositiveAutomaticFeedbackPipe,
        HtmlForMarkdownPipe,
        TruncatePipe,
        SanitizeHtmlPipe,
        SecuredImageComponent,
    ],
})
export class ArtemisSharedModule {
    static forRoot() {
        return {
            ngModule: ArtemisSharedModule,
        };
    }
}

import { DatePipe } from '@angular/common';
import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { NgbDateAdapter } from '@ng-bootstrap/ng-bootstrap';
import {
    ArTEMiSSharedCommonModule,
    ArTEMiSSharedLibsModule,
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
import { SecuredImageComponent } from 'app/components/util/secured-image.component';
import { ExerciseTypePipe } from 'app/entities/exercise';
import { RemovePositiveAutomaticFeedbackPipe } from 'app/shared/pipes/remove-positive-automatic-feedback.pipe';
import { SanitizeHtmlPipe } from 'app/shared/pipes/sanitize-html.pipe';

@NgModule({
    imports: [ArTEMiSSharedLibsModule, ArTEMiSSharedCommonModule],
    declarations: [
        HasAnyAuthorityDirective,
        SafeHtmlPipe,
        SafeUrlPipe,
        RemoveKeysPipe,
        SecuredImageComponent,
        ExerciseTypePipe,
        KeysPipe,
        TypeCheckPipe,
        RemovePositiveAutomaticFeedbackPipe,
        HtmlForMarkdownPipe,
        TruncatePipe,
        SanitizeHtmlPipe,
    ],
    providers: [FileService, FileUploaderService, DatePipe, { provide: NgbDateAdapter, useClass: NgbDateMomentAdapter }],
    entryComponents: [],
    exports: [
        ArTEMiSSharedCommonModule,
        HasAnyAuthorityDirective,
        DatePipe,
        SafeHtmlPipe,
        SafeUrlPipe,
        RemoveKeysPipe,
        SecuredImageComponent,
        ExerciseTypePipe,
        KeysPipe,
        TypeCheckPipe,
        RemovePositiveAutomaticFeedbackPipe,
        HtmlForMarkdownPipe,
        TruncatePipe,
        SanitizeHtmlPipe,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSSharedModule {
    static forRoot() {
        return {
            ngModule: ArTEMiSSharedModule,
        };
    }
}

import { DatePipe } from '@angular/common';
import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { NgbDateAdapter } from '@ng-bootstrap/ng-bootstrap';
import { ArTEMiSSharedCommonModule, ArTEMiSSharedLibsModule, HasAnyAuthorityDirective, RemoveKeysPipe, SafeHtmlPipe, SafeUrlPipe, JhiDynamicTranslateDirective } from './';
import { FileUploaderService } from './http/file-uploader.service';
import { NgbDateMomentAdapter } from './util/datepicker-adapter';
import { SecuredImageComponent } from 'app/components/util/secured-image.component';
import { EscapeHtmlPipe } from './pipes/keep-html.pipe';

@NgModule({
    imports: [ArTEMiSSharedLibsModule, ArTEMiSSharedCommonModule],
    declarations: [HasAnyAuthorityDirective, SafeHtmlPipe, SafeUrlPipe, RemoveKeysPipe, JhiDynamicTranslateDirective, SecuredImageComponent, EscapeHtmlPipe],
    providers: [FileUploaderService, DatePipe, { provide: NgbDateAdapter, useClass: NgbDateMomentAdapter }],
    entryComponents: [],
    exports: [
        ArTEMiSSharedCommonModule,
        HasAnyAuthorityDirective,
        DatePipe,
        SafeHtmlPipe,
        SafeUrlPipe,
        RemoveKeysPipe,
        JhiDynamicTranslateDirective,
        SecuredImageComponent,
        EscapeHtmlPipe,
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

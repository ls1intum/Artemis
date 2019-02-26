import { DatePipe } from '@angular/common';
import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { NgbDateAdapter } from '@ng-bootstrap/ng-bootstrap';
import { ArTEMiSSharedCommonModule, ArTEMiSSharedLibsModule, HasAnyAuthorityDirective, JhiLoginComponent, RemoveKeysPipe, SafeHtmlPipe, SafeUrlPipe, JhiDynamicTranslateDirective } from './';
import { FileUploaderService } from './http/file-uploader.service';
import { NgbDateMomentAdapter } from './util/datepicker-adapter';

@NgModule({
    imports: [ArTEMiSSharedLibsModule, ArTEMiSSharedCommonModule],
    declarations: [JhiLoginComponent, HasAnyAuthorityDirective, SafeHtmlPipe, SafeUrlPipe, RemoveKeysPipe, JhiDynamicTranslateDirective],
    providers: [FileUploaderService, DatePipe, { provide: NgbDateAdapter, useClass: NgbDateMomentAdapter }],
    entryComponents: [JhiLoginComponent],
    exports: [ArTEMiSSharedCommonModule, JhiLoginComponent, HasAnyAuthorityDirective, DatePipe, SafeHtmlPipe, SafeUrlPipe, RemoveKeysPipe, JhiDynamicTranslateDirective],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSSharedModule {
    static forRoot() {
        return {
            ngModule: ArTEMiSSharedModule
        };
    }
}

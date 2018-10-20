import { DatePipe } from '@angular/common';
import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { NgbDateAdapter } from '@ng-bootstrap/ng-bootstrap';
import {
    ArTEMiSSharedLibsModule,
    ArTEMiSSharedCommonModule,
    JhiLoginModalComponent,
    HasAnyAuthorityDirective,
    SafeHtmlPipe,
    SafeUrlPipe
} from './';
import { FileUploaderService } from './http/file-uploader.service';
import { NgbDateMomentAdapter } from './util/datepicker-adapter';

@NgModule({
    imports: [ArTEMiSSharedLibsModule, ArTEMiSSharedCommonModule],
    declarations: [JhiLoginModalComponent, HasAnyAuthorityDirective, SafeHtmlPipe, SafeUrlPipe],
    providers: [FileUploaderService, DatePipe, { provide: NgbDateAdapter, useClass: NgbDateMomentAdapter }],
    entryComponents: [JhiLoginModalComponent],
    exports: [ArTEMiSSharedCommonModule, JhiLoginModalComponent, HasAnyAuthorityDirective, DatePipe, SafeHtmlPipe, SafeUrlPipe],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSSharedModule {}

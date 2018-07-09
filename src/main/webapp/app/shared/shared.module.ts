import { DatePipe } from '@angular/common';
import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import {
    AccountService,
    ArTEMiSSharedCommonModule,
    ArTEMiSSharedLibsModule,
    AuthServerProvider,
    CSRFService,
    HasAnyAuthorityDirective,
    JhiLoginModalComponent,
    LoginModalService,
    LoginService,
    Principal, SafeHtmlPipe,
    StateStorageService,
    UserService
} from './';
import { FileUploaderService } from './http/file-uploader.service';

@NgModule({
    imports: [ArTEMiSSharedLibsModule, ArTEMiSSharedCommonModule],
    declarations: [JhiLoginModalComponent, HasAnyAuthorityDirective, SafeHtmlPipe],
    providers: [
        LoginService,
        LoginModalService,
        AccountService,
        StateStorageService,
        Principal,
        CSRFService,
        AuthServerProvider,
        UserService,
        FileUploaderService,
        DatePipe
    ],
    entryComponents: [JhiLoginModalComponent],
    exports: [ArTEMiSSharedCommonModule, JhiLoginModalComponent, HasAnyAuthorityDirective, DatePipe, SafeHtmlPipe],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSSharedModule {}

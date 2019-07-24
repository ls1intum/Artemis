import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { CustomStudentLoginComponent } from 'app/components/custom-student-login/custom-student-login.component';

@NgModule({
    imports: [ArTEMiSSharedModule, BrowserAnimationsModule],
    declarations: [CustomStudentLoginComponent],
    exports: [CustomStudentLoginComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSCustomStudentLogin {}

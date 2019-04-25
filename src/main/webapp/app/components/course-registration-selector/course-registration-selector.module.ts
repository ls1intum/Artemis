import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSColorSelectorModule } from 'app/components/color-selector/color-selector.module';
import { TagInputModule } from 'ngx-chips';
import { ReactiveFormsModule } from '@angular/forms';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { CourseRegistrationSelectorComponent } from 'app/components/course-registration-selector/course-registration-selector.component';

@NgModule({
    imports: [ArTEMiSSharedModule, ArTEMiSColorSelectorModule, ReactiveFormsModule, TagInputModule, BrowserAnimationsModule],
    declarations: [CourseRegistrationSelectorComponent],
    exports: [CourseRegistrationSelectorComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSCourseRegistrationSelector {}

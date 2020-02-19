import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisColorSelectorModule } from 'app/shared/color-selector/color-selector.module';
import { TagInputModule } from 'ngx-chips';
import { ReactiveFormsModule } from '@angular/forms';
import { CourseRegistrationSelectorComponent } from 'app/course/course-registration-selector/course-registration-selector.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisColorSelectorModule, ReactiveFormsModule, TagInputModule, ArtemisSharedComponentModule],
    declarations: [CourseRegistrationSelectorComponent],
    exports: [CourseRegistrationSelectorComponent],
})
export class ArtemisCourseRegistrationSelector {}

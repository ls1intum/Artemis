import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CourseUsersSelectorComponent } from 'app/shared/course-users-selector/course-users-selector.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [CommonModule, FormsModule, ReactiveFormsModule, ArtemisSharedModule, ArtemisSharedComponentModule],
    exports: [CourseUsersSelectorComponent],
    declarations: [CourseUsersSelectorComponent],
})
export class CourseUsersSelectorModule {}

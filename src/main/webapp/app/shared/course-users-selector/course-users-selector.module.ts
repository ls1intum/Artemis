import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { NgModule } from '@angular/core';
import { CourseUsersSelectorComponent } from 'app/shared/course-users-selector/course-users-selector.component';

@NgModule({
    imports: [CommonModule, FormsModule, ReactiveFormsModule, ArtemisSharedModule, ArtemisSharedComponentModule],
    exports: [CourseUsersSelectorComponent],
    declarations: [CourseUsersSelectorComponent],
})
export class CourseUsersSelectorModule {}

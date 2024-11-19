import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { NgModule } from '@angular/core';
import { CourseUsersSelectorComponent } from 'app/shared/course-users-selector/course-users-selector.component';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';

@NgModule({
    imports: [CommonModule, FormsModule, ReactiveFormsModule, ArtemisSharedModule, ArtemisSharedComponentModule, ProfilePictureComponent],
    exports: [CourseUsersSelectorComponent],
    declarations: [CourseUsersSelectorComponent],
})
export class CourseUsersSelectorModule {}

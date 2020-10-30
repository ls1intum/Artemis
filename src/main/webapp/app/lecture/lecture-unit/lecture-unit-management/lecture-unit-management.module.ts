import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component';
import { NgModule } from '@angular/core';
import { lectureUnitRoute } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.route';
import { RouterModule } from '@angular/router';
import { UnitCreationCardComponent } from './unit-creation-card/unit-creation-card.component';
import { CreateExerciseUnitComponent } from './create-exercise-unit/create-exercise-unit.component';
import { ArtemisCoursesModule } from 'app/overview/courses.module';
import { CreateAttachmentUnitComponent } from './create-attachment-unit/create-attachment-unit.component';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ReactiveFormsModule } from '@angular/forms';
import { AttachmentUnitFormComponent } from './attachment-unit-form/attachment-unit-form.component';
import { EditAttachmentUnitComponent } from './edit-attachment-unit/edit-attachment-unit.component';

const ENTITY_STATES = [...lectureUnitRoute];

@NgModule({
    imports: [ArtemisSharedModule, ReactiveFormsModule, ArtemisSharedComponentModule, RouterModule.forChild(ENTITY_STATES), ArtemisCoursesModule, FormDateTimePickerModule],
    declarations: [
        LectureUnitManagementComponent,
        UnitCreationCardComponent,
        CreateExerciseUnitComponent,
        CreateAttachmentUnitComponent,
        AttachmentUnitFormComponent,
        EditAttachmentUnitComponent,
    ],
})
export class ArtemisLectureUnitManagementModule {}

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { UnitCreationCardComponent } from './unit-creation-card/unit-creation-card.component';
import { CreateExerciseUnitComponent } from './create-exercise-unit/create-exercise-unit.component';
import { CreateAttachmentUnitComponent } from './create-attachment-unit/create-attachment-unit.component';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ReactiveFormsModule } from '@angular/forms';
import { AttachmentUnitFormComponent } from './attachment-unit-form/attachment-unit-form.component';
import { EditAttachmentUnitComponent } from './edit-attachment-unit/edit-attachment-unit.component';
import { CreateVideoUnitComponent } from './create-video-unit/create-video-unit.component';
import { EditVideoUnitComponent } from './edit-video-unit/edit-video-unit.component';
import { VideoUnitFormComponent } from './video-unit-form/video-unit-form.component';
import { CreateTextUnitComponent } from './create-text-unit/create-text-unit.component';
import { TextUnitFormComponent } from './text-unit-form/text-unit-form.component';
import { EditTextUnitComponent } from './edit-text-unit/edit-text-unit.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { LectureUnitLayoutComponent } from './lecture-unit-layout/lecture-unit-layout.component';
import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { EditOnlineUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-online-unit/edit-online-unit.component';
import { CreateOnlineUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-online-unit/create-online-unit.component';
import { OnlineUnitFormComponent } from 'app/lecture/lecture-unit/lecture-unit-management/online-unit-form/online-unit-form.component';

@NgModule({
    imports: [
        ArtemisMarkdownEditorModule,
        ArtemisSharedModule,
        ReactiveFormsModule,
        ArtemisSharedComponentModule,
        RouterModule.forChild([]),
        ArtemisLectureUnitsModule,
        FormDateTimePickerModule,
        ArtemisLearningGoalsModule,
    ],
    declarations: [
        LectureUnitManagementComponent,
        UnitCreationCardComponent,
        LectureUnitLayoutComponent,
        // Exercise
        CreateExerciseUnitComponent,
        // Attachment
        CreateAttachmentUnitComponent,
        EditAttachmentUnitComponent,
        AttachmentUnitFormComponent,
        // Video
        CreateVideoUnitComponent,
        EditVideoUnitComponent,
        VideoUnitFormComponent,
        // Text
        CreateTextUnitComponent,
        EditTextUnitComponent,
        TextUnitFormComponent,
        // Online
        CreateOnlineUnitComponent,
        EditOnlineUnitComponent,
        OnlineUnitFormComponent,
    ],
})
export class ArtemisLectureUnitManagementModule {}

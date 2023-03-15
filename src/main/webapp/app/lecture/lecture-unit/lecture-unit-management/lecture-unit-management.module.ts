import { DragDropModule } from '@angular/cdk/drag-drop';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { AttachmentUnitFormComponent } from './attachment-unit-form/attachment-unit-form.component';
import { AttachmentUnitsComponent } from './attachment-units/attachment-units.component';
import { CreateAttachmentUnitComponent } from './create-attachment-unit/create-attachment-unit.component';
import { CreateExerciseUnitComponent } from './create-exercise-unit/create-exercise-unit.component';
import { CreateTextUnitComponent } from './create-text-unit/create-text-unit.component';
import { CreateVideoUnitComponent } from './create-video-unit/create-video-unit.component';
import { EditAttachmentUnitComponent } from './edit-attachment-unit/edit-attachment-unit.component';
import { EditTextUnitComponent } from './edit-text-unit/edit-text-unit.component';
import { EditVideoUnitComponent } from './edit-video-unit/edit-video-unit.component';
import { LectureUnitLayoutComponent } from './lecture-unit-layout/lecture-unit-layout.component';
import { TextUnitFormComponent } from './text-unit-form/text-unit-form.component';
import { UnitCreationCardComponent } from './unit-creation-card/unit-creation-card.component';
import { VideoUnitFormComponent } from './video-unit-form/video-unit-form.component';
import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';
import { CreateOnlineUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-online-unit/create-online-unit.component';
import { EditOnlineUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-online-unit/edit-online-unit.component';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component';
import { OnlineUnitFormComponent } from 'app/lecture/lecture-unit/lecture-unit-management/online-unit-form/online-unit-form.component';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

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
        DragDropModule,
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
        AttachmentUnitsComponent,
    ],
    exports: [
        LectureUnitManagementComponent,
        UnitCreationCardComponent,
        TextUnitFormComponent,
        VideoUnitFormComponent,
        OnlineUnitFormComponent,
        AttachmentUnitFormComponent,
        CreateExerciseUnitComponent,
    ],
})
export class ArtemisLectureUnitManagementModule {}

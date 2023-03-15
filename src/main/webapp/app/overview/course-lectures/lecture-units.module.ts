import { NgModule } from '@angular/core';

import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';
import { OnlineUnitComponent } from 'app/overview/course-lectures/online-unit/online-unit.component';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisSharedPipesModule, ArtemisCourseExerciseRowModule, ArtemisMarkdownModule],
    declarations: [ExerciseUnitComponent, AttachmentUnitComponent, VideoUnitComponent, TextUnitComponent, OnlineUnitComponent],
    exports: [ExerciseUnitComponent, AttachmentUnitComponent, VideoUnitComponent, TextUnitComponent, OnlineUnitComponent],
})
export class ArtemisLectureUnitsModule {}

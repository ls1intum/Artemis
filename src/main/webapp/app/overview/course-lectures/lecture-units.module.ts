import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { OnlineUnitComponent } from 'app/overview/course-lectures/online-unit/online-unit.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisSharedPipesModule, ArtemisCourseExerciseRowModule, ArtemisMarkdownModule],
    declarations: [ExerciseUnitComponent, AttachmentUnitComponent, VideoUnitComponent, TextUnitComponent, OnlineUnitComponent],
    exports: [ExerciseUnitComponent, AttachmentUnitComponent, VideoUnitComponent, TextUnitComponent, OnlineUnitComponent],
})
export class ArtemisLectureUnitsModule {}

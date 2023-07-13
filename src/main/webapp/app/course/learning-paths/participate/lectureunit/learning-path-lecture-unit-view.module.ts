import { NgModule } from '@angular/core';
import { LearningPathLectureUnitViewComponent } from 'app/course/learning-paths/participate/lectureunit/learning-path-lecture-unit-view.component';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisLectureUnitsModule],
    declarations: [LearningPathLectureUnitViewComponent],
    exports: [LearningPathLectureUnitViewComponent],
})
export class ArtemisLearningPathLectureUnitViewModule {}

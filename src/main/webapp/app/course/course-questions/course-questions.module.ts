import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseQuestionsComponent } from 'app/course/course-questions/course-questions.component';
import { ArtemisCourseQuestionsRoutingModule } from 'app/course/course-questions/course-questions-routing.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisCourseQuestionsRoutingModule],
    declarations: [CourseQuestionsComponent],
})
export class ArtemisCourseQuestionsModule {}

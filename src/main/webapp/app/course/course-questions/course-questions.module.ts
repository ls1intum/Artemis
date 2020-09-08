import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseQuestionsComponent } from 'app/course/course-questions/course-questions.component';
import { RouterModule } from '@angular/router';
import { courseQuestionsRoute } from 'app/course/course-questions/course-questions.route';

const ENTITY_STATES = [...courseQuestionsRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES),],
    declarations: [CourseQuestionsComponent],
})
export class ArtemisCourseQuestionsModule {}

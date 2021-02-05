import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseQuestionsComponent } from 'app/course/course-questions/course-questions.component';
import { ArtemisCourseQuestionsRoutingModule } from 'app/course/course-questions/course-questions-routing.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisCourseQuestionsRoutingModule, ArtemisSharedComponentModule, ArtemisMarkdownModule],
    declarations: [CourseQuestionsComponent],
})
export class ArtemisCourseQuestionsModule {}

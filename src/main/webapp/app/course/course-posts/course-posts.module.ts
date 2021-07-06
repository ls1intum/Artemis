import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CoursePostsComponent } from 'app/course/course-posts/course-posts.component';
import { ArtemisCoursePostsRoutingModule } from 'app/course/course-posts/course-posts-routing.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisCoursePostsRoutingModule, ArtemisSharedComponentModule, ArtemisMarkdownModule],
    declarations: [CoursePostsComponent],
})
export class ArtemisCoursePostsModule {}

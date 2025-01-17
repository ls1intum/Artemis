import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { NgModule } from '@angular/core';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSidebarModule } from 'app/shared/sidebar/sidebar.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedCommonModule, ArtemisSharedComponentModule, ArtemisSidebarModule, CourseExamsComponent],
    exports: [CourseExamsComponent],
})
export class CourseExamsModule {}

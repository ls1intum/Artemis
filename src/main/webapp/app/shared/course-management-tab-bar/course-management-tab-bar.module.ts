import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CourseManagementTabBarComponent } from 'app/shared/course-management-tab-bar/course-management-tab-bar.component';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisCoursesModule } from 'app/overview/courses.module';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule, ArtemisSharedComponentModule, FeatureToggleModule, ArtemisCoursesModule],
    declarations: [CourseManagementTabBarComponent],
    exports: [CourseManagementTabBarComponent],
})
export class CourseManagementTabBarModule {}

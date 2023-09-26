import { routes } from './course-exams.route';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { CourseExamDetailComponent } from 'app/overview/course-exams/course-exam-detail/course-exam-detail.component';
import { CourseExamAttemptReviewDetailComponent } from 'app/overview/course-exams/course-exam-attempt-review-detail/course-exam-attempt-review-detail.component';
import { ArtemisExamSharedModule } from 'app/exam/shared/exam-shared.module';
import { RouterModule } from '@angular/router';
import { NgModule } from '@angular/core';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [RouterModule.forChild(routes), ArtemisSharedModule, ArtemisSharedCommonModule, ArtemisSharedComponentModule, ArtemisExamSharedModule],
    declarations: [CourseExamsComponent, CourseExamDetailComponent, CourseExamAttemptReviewDetailComponent],
    exports: [CourseExamsComponent],
})
export class CourseExamsModule {}

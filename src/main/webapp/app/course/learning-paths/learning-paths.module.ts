import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { LearningPathManagementComponent } from 'app/course/learning-paths/learning-path-management/learning-path-management.component';

@NgModule({
    imports: [ArtemisSharedModule, FormsModule, ReactiveFormsModule, ArtemisSharedComponentModule],
    declarations: [LearningPathManagementComponent],
    exports: [],
})
export class ArtemisLearningPathsModule {}

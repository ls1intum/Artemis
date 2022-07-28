import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { DetailedGradingSystemComponent } from 'app/grading-system/detailed-grading-system/detailed-grading-system.component';
import { GradingSystemInfoModalComponent } from 'app/grading-system/grading-system-info-modal/grading-system-info-modal.component';
import { GradingSystemComponent } from 'app/grading-system/grading-system.component';
import { gradingSystemState } from 'app/grading-system/grading-system.route';
import { RouterModule } from '@angular/router';
import { IntervalGradingSystemComponent } from 'app/grading-system/interval-grading-system/interval-grading-system.component';
import { ArtemisModePickerModule } from 'app/exercises/shared/mode-picker/mode-picker.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    declarations: [GradingSystemComponent, DetailedGradingSystemComponent, IntervalGradingSystemComponent, GradingSystemInfoModalComponent],
    imports: [ArtemisSharedModule, RouterModule.forChild(gradingSystemState), ArtemisModePickerModule, ArtemisSharedComponentModule],
    exports: [GradingSystemComponent, GradingSystemInfoModalComponent],
})
export class GradingSystemModule {}

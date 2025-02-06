import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';

import { LectureUpdateComponent } from 'app/lecture/lecture-update.component';
import { LectureComponent } from 'app/lecture/lecture.component';
import { LectureDetailComponent } from 'app/lecture/lecture-detail.component';
import { LectureAttachmentsComponent } from 'app/lecture/lecture-attachments.component';
import { lectureRoute } from 'app/lecture/lecture.route';
import { ArtemisLectureUnitManagementModule } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.module';
import { LectureImportComponent } from 'app/lecture/lecture-import.component';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { LectureTitleChannelNameComponent } from 'app/lecture/lecture-title-channel-name.component';
import { DetailModule } from 'app/detail-overview-list/detail.module';
import { CompetencyFormComponent } from 'app/course/competencies/forms/competency/competency-form.component';
import { ArtemisFormsModule } from 'app/forms/artemis-forms.module';
import { LectureUpdateUnitsComponent } from 'app/lecture/lecture-units/lecture-units.component';
import { LectureUpdatePeriodComponent } from 'app/lecture/lecture-period/lecture-period.component';

const ENTITY_STATES = [...lectureRoute];

@NgModule({
    imports: [
        RouterModule.forChild(ENTITY_STATES),
        ArtemisLectureUnitManagementModule,
        FormDateTimePickerModule,

        ArtemisCompetenciesModule,
        DetailModule,
        CompetencyFormComponent,
        ArtemisFormsModule,
        LectureComponent,
        LectureDetailComponent,
        LectureImportComponent,
        LectureUpdateComponent,
        LectureAttachmentsComponent,
        LectureTitleChannelNameComponent,
        LectureUpdateUnitsComponent,
        LectureUpdatePeriodComponent,
    ],
})
export class ArtemisLectureModule {}

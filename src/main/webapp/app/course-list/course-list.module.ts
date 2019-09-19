import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedCommonModule, ArtemisSharedModule } from '../shared';
import { courseListRoute } from './course-list.route';
import { CourseExerciseService, CourseScoreCalculationComponent, CourseScoreCalculationService, CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { CourseListComponent } from './course-list.component';
import { ExerciseListComponent, ShowExercisePipe } from './exercise-list/exercise-list.component';
import { RepositoryService } from '../entities/repository/repository.service';
import { ArtemisResultModule, ResultComponent, ResultService } from '../entities/result';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'ngx-moment';
import { JhiMainComponent } from '../layouts';
import { ClipboardModule } from 'ngx-clipboard';

const ENTITY_STATES = [...courseListRoute];

@NgModule({
    imports: [ArtemisSharedCommonModule, ArtemisSharedModule, ArtemisResultModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [CourseListComponent, CourseScoreCalculationComponent, ExerciseListComponent, ShowExercisePipe],
    exports: [ResultComponent],
    entryComponents: [CourseListComponent, CourseScoreCalculationComponent, JhiMainComponent, ResultComponent],
    providers: [CourseService, JhiAlertService, RepositoryService, ResultService, CourseExerciseService, ParticipationService, CourseScoreCalculationService],
})
export class ArtemisCourseListModule {}

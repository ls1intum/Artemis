import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../shared';
import { courseListRoute } from './course-list.route';
import { CourseExerciseService, CourseScoreCalculationComponent, CourseScoreCalculationService, CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { CourseListComponent } from './course-list.component';
import { ExerciseListComponent, ShowExercisePipe } from './exercise-list/exercise-list.component';
import { RepositoryService } from '../entities/repository/repository.service';
import { ArTEMiSResultModule, ResultComponent, ResultService } from '../entities/result';
import { HomeComponent } from '../home';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'angular2-moment';
import { JhiMainComponent } from '../layouts';
import { ClipboardModule } from 'ngx-clipboard';

const ENTITY_STATES = [...courseListRoute];

@NgModule({
    imports: [BrowserModule, ArTEMiSSharedModule, ArTEMiSResultModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [CourseListComponent, CourseScoreCalculationComponent, ExerciseListComponent, ShowExercisePipe],
    exports: [ResultComponent],
    entryComponents: [HomeComponent, CourseListComponent, CourseScoreCalculationComponent, JhiMainComponent, ResultComponent],
    providers: [
        CourseService,
        JhiAlertService,
        RepositoryService,
        ResultService,
        CourseExerciseService,
        ParticipationService,
        CourseScoreCalculationService
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSCourseListModule {}

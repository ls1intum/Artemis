import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../shared';
import { coursesRoute } from './courses.route';
import { CourseComponent, CourseExerciseService, CourseScoreCalculationService, CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { CoursesComponent } from './courses.component';
import { JhiResultDetailComponent, ResultComponent, SafeHtmlPipe } from './results/result.component';
import { ExerciseListComponent, ShowExercisePipe } from './exercises/exercise-list.component';
import { RepositoryService } from '../entities/repository/repository.service';
import { ResultService } from '../entities/result';
import { HomeComponent } from '../home';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'angular2-moment';
import { JhiMainComponent } from '../layouts';

const ENTITY_STATES = [
    ...coursesRoute
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        MomentModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        CoursesComponent,
        ResultComponent,
        JhiResultDetailComponent,
        ExerciseListComponent,
        ShowExercisePipe,
        SafeHtmlPipe
    ],
    exports: [
        ResultComponent
    ],
    entryComponents: [
        HomeComponent,
        CourseComponent,
        JhiMainComponent
    ],
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
export class ArTEMiSCoursesModule {}

import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../shared';
import { coursesRoute } from './courses.route';
import { CourseComponent, CourseExerciseService, CourseService, CourseScoreCalculationService  } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { CoursesComponent } from './courses.component';
import { ExerciseListComponent, IsNotOverduePipe } from './exercises/exercise-list.component';
import { RepositoryService } from '../entities/repository/repository.service';
import { ResultService } from '../entities/result';
import { HomeComponent } from '../home';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'angular2-moment';
import { JhiMainComponent } from '../layouts';
import { ArTEMiSResultModule, ResultComponent } from '../entities/result';

const ENTITY_STATES = [
    ...coursesRoute
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        ArTEMiSResultModule,
        MomentModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        CoursesComponent,
        ExerciseListComponent,
        IsNotOverduePipe
    ],
    entryComponents: [
        HomeComponent,
        CourseComponent,
        JhiMainComponent,
        ResultComponent
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

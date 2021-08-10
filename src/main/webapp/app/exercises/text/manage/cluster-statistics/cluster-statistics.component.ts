import { Component, Input, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextExerciseService } from '../text-exercise/text-exercise.service';
import { CourseExerciseService, CourseManagementService } from 'app/course/manage/course-management.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseComponent } from 'app/exercises/shared/exercise/exercise.component';
import { TranslateService } from '@ngx-translate/core';
import { onError } from 'app/shared/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { SortService } from 'app/shared/service/sort.service';
import { TextExerciseImportComponent } from 'app/exercises/text/manage/text-exercise-import.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-text-exercise-cluster-statistics',
    templateUrl: './cluster-statistics.component.html',
})
export class ClusterStatisticsComponent implements OnInit {
    constructor() {}

    ngOnInit(): void {
        // throw new Error('Method not implemented.');
    }
}

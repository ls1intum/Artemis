import { JhiAlertService } from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { ModelingExerciseService } from 'app/entities/modeling-exercise';

@Component({
    selector: 'jhi-assessment-dashboard',
    templateUrl: './modeling-statistics.component.html',
    providers: [JhiAlertService, ModelingExerciseService]
})
export class ModelingStatisticsComponent implements OnInit, OnDestroy {
    statistics: String;
    paramSub: Subscription;

    constructor(
        private route: ActivatedRoute,
        private jhiAlertService: JhiAlertService,
        private router: Router,
        private modelingExerciseService: ModelingExerciseService
    ) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            this.modelingExerciseService.getStatistics(params['exerciseId']).subscribe((res: HttpResponse<String>) => {
                this.statistics = JSON.stringify(res.body);
            });
        });
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }
}

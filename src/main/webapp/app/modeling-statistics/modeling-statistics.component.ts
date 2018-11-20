import { JhiAlertService } from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { ModelingExerciseService } from 'app/entities/modeling-exercise';
import { ModelingStatistic } from 'app/entities/modeling-statistic';

@Component({
    selector: 'jhi-assessment-dashboard',
    templateUrl: './modeling-statistics.component.html',
    providers: [JhiAlertService, ModelingExerciseService]
})
export class ModelingStatisticsComponent implements OnInit, OnDestroy {
    statistics: ModelingStatistic;
    paramSub: Subscription;
    modelIds: string[] = [];
    elementIds: string[] = [];

    constructor(
        private route: ActivatedRoute,
        private jhiAlertService: JhiAlertService,
        private router: Router,
        private modelingExerciseService: ModelingExerciseService
    ) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            this.modelingExerciseService.getStatistics(params['exerciseId']).subscribe((res: HttpResponse<ModelingStatistic>) => {
                this.statistics = res.body;
                for (const key of Object.keys(this.statistics.models)) {
                    this.modelIds.push(key);
                }
                for (const key of Object.keys(this.statistics.uniqueElements)) {
                    this.elementIds.push(key);
                }
            });
        });
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }
}

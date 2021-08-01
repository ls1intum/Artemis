import { AlertService } from 'app/core/util/alert.service';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { ModelingStatistic } from 'app/entities/modeling-statistic.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';

@Component({
    selector: 'jhi-statistic-dashboard',
    templateUrl: './modeling-statistics.component.html',
    providers: [ModelingExerciseService],
})
export class ModelingStatisticsComponent implements OnInit, OnDestroy {
    statistics: ModelingStatistic;
    paramSub: Subscription;
    modelIds: string[] = [];
    elementIds: string[] = [];

    constructor(private route: ActivatedRoute, private alertService: AlertService, private router: Router, private modelingExerciseService: ModelingExerciseService) {}

    /**
     * Displays the statistics for this modeling exercise on initialization
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.modelingExerciseService.getStatistics(params['exerciseId']).subscribe((res: HttpResponse<ModelingStatistic>) => {
                this.statistics = res.body!;
                for (const key of Object.keys(this.statistics.models)) {
                    this.modelIds.push(key);
                }
                for (const key of Object.keys(this.statistics.uniqueElements)) {
                    this.elementIds.push(key);
                }
            });
        });
    }

    /**
     * Unsubscribe the parameter subscription on component destruction
     */
    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }
}

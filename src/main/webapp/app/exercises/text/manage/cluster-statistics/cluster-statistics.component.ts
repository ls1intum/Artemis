import { Component, Input, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { TextExerciseService } from '../text-exercise/text-exercise.service';
import { ActivatedRoute } from '@angular/router';

export interface ClusterInfo {
    clusterId: number;
    clusterSize: number;
    numberOfAutomaticFeedbacks: number;
}

@Component({
    selector: 'jhi-text-exercise-cluster-statistics',
    templateUrl: './cluster-statistics.component.html',
})
export class ClusterStatisticsComponent implements OnInit {
    readonly MIN_POINTS_GREEN = 100;
    readonly MIN_POINTS_ORANGE = 50;
    clusters: ClusterInfo[] = [];

    constructor(private textExerciseService: TextExerciseService, private route: ActivatedRoute, jhiAlertService: JhiAlertService) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            const exerciseId = Number(params['exerciseId']);
            this.loadClusterFromExercise(exerciseId);
        });
    }

    loadClusterFromExercise(exerciseId: number) {
        this.textExerciseService.getClusterStats(exerciseId).subscribe({
            next: (res: HttpResponse<ClusterInfo[]>) => {
                console.error(res.body, 'RESSSS!');
                this.clusters = res.body!;
            },
        });
    }
}

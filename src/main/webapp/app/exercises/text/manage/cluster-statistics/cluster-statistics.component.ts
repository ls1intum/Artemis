import { Component, Input, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { TextExerciseService } from '../text-exercise/text-exercise.service';
import { ActivatedRoute } from '@angular/router';

export interface ClusterInfo {
    clusterId: number;
    clusterSize: number;
    numberOfAutomaticFeedbacks: number;
    disabled: boolean;
}

@Component({
    selector: 'jhi-text-exercise-cluster-statistics',
    templateUrl: './cluster-statistics.component.html',
})
export class ClusterStatisticsComponent implements OnInit {
    readonly MIN_POINTS_GREEN = 100;
    readonly MIN_POINTS_ORANGE = 50;
    clusters: ClusterInfo[] = [];
    currentExerciseId: number;

    constructor(private textExerciseService: TextExerciseService, private route: ActivatedRoute, jhiAlertService: JhiAlertService) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.currentExerciseId = Number(params['exerciseId']);
            this.loadClusterFromExercise(this.currentExerciseId);
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

    setClusterDisabledPredicate(clusterId: number, disabled: boolean): void {
        this.textExerciseService.setClusterDisabledPredicate(clusterId, disabled).subscribe(() => {
            // reload content again
            this.loadClusterFromExercise(this.currentExerciseId);
        });
    }
}

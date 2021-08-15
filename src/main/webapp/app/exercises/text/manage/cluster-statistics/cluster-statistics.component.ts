import { Component, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { TextExerciseService } from '../text-exercise/text-exercise.service';
import { ActivatedRoute } from '@angular/router';
import { TextExerciseClusterStatistics } from 'app/entities/text-exercise-cluster-statistics.model';

@Component({
    selector: 'jhi-text-exercise-cluster-statistics',
    templateUrl: './cluster-statistics.component.html',
})
export class ClusterStatisticsComponent implements OnInit {
    clusters: TextExerciseClusterStatistics[] = [];
    currentExerciseId: number;

    constructor(private textExerciseService: TextExerciseService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.currentExerciseId = Number(params['exerciseId']);
            this.loadClusterFromExercise(this.currentExerciseId);
        });
    }

    loadClusterFromExercise(exerciseId: number) {
        this.textExerciseService.getClusterStats(exerciseId).subscribe({
            next: (res: HttpResponse<TextExerciseClusterStatistics[]>) => {
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

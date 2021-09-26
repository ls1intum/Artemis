import { Component, OnInit } from '@angular/core';
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

    /**
     * Subscribes to api call that fetched cluster statistics data and sets this components clusters upon successful fetching
     * @param exerciseId The id of the exercise to fetch the cluster stats for
     */
    loadClusterFromExercise(exerciseId: number) {
        this.textExerciseService.getClusterStats(exerciseId).subscribe((clusterStatistics: TextExerciseClusterStatistics[]) => {
            this.clusters = clusterStatistics;
        });
    }

    /**
     * Sets the cluster disabled predicate and reloads the cluster data to refresh the table
     * @param clusterId The id of the cluster to disable
     * @param disabled The predicate specifying whether the cluster should be disabled or not
     */
    setClusterDisabledPredicate(clusterId: number, disabled: boolean): void {
        this.textExerciseService.setClusterDisabledPredicate(this.currentExerciseId, clusterId, disabled).subscribe(() => {
            // reload content again
            this.loadClusterFromExercise(this.currentExerciseId);
        });
    }
}

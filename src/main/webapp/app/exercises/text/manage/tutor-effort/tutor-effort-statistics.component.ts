import { Component, OnInit } from '@angular/core';
import { TutorEffort } from 'app/entities/tutor-effort.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-text-exercise-tutor-effort-statistics',
    templateUrl: './tutor-effort-statistics.component.html',
})
export class TutorEffortStatisticsComponent implements OnInit {
    tutorEfforts: TutorEffort[];
    numberOfSubmissions: number;
    totalTimeSpent: number;
    averageTimeSpent: number;
    currentExerciseId: number;
    currentCourseId: number;

    constructor(private textExerciseService: TextExerciseService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.currentExerciseId = Number(params['exerciseId']);
            this.currentCourseId = Number(params['courseId']);
        });
    }

    loadTutorEfforts() {
        this.textExerciseService.calculateTutorEffort(this.currentCourseId, this.currentExerciseId).subscribe(
            (res: TutorEffort[]) => {
                this.tutorEfforts = res!;
                console.log('test', this.tutorEfforts);
                this.numberOfSubmissions = this.tutorEfforts.reduce((n, { numberOfSubmissionsAssessed }) => n + numberOfSubmissionsAssessed, 0);
                this.totalTimeSpent = this.tutorEfforts.reduce((n, { totalTimeSpentMinutes }) => n + totalTimeSpentMinutes, 0);
                const avgTemp = this.numberOfSubmissions / this.totalTimeSpent;
                if (avgTemp) {
                    this.averageTimeSpent = Math.round((avgTemp + Number.EPSILON) * 100) / 100;
                }
            },
            (error) => {
                console.error('Error while retrieving tutor effort statistics:', error);
            },
        );
    }
}

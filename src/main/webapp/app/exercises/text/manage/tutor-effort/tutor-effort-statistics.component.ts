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
            },
            (error) => {
                console.error('Error while retrieving tutor effort statistics:', error);
            },
        );
    }
}

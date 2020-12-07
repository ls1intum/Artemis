import { Component, Input, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute, Router } from '@angular/router';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import * as moment from 'moment';
import { CourseExerciseStatisticsDTO } from 'app/exercises/shared/exercise/exercise-statistics-dto.model';
import { ExerciseRowType } from './course-management-exercise-row.component';

@Component({
    selector: 'jhi-course-management-card',
    templateUrl: './course-management-card.component.html',
    styleUrls: ['course-management-card.scss'],
})
export class CourseManagementCardComponent implements OnInit {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    @Input() course: Course;
    futureExercises: Exercise[];
    currentExercises: Exercise[];
    pastExercises: Exercise[];
    showFutureExercises = true;
    showCurrentExercises = true;
    showPastExercises = false;

    // Expose enums to the template
    exerciseType = ExerciseType;
    exerciseRowType = ExerciseRowType;

    private statistics = new Map<number, CourseExerciseStatisticsDTO>();

    constructor(private router: Router, private route: ActivatedRoute, private courseManagementService: CourseManagementService, private jhiAlertService: JhiAlertService) {}

    ngOnInit() {
        this.courseManagementService.findWithExercises(this.course.id!).subscribe(
            (result: HttpResponse<Course>) => {
                const exercises = result.body!.exercises!;
                this.futureExercises = exercises.filter((e) => e.releaseDate && e.releaseDate > moment());
                this.currentExercises = exercises.filter((e) => (!e.releaseDate || e.releaseDate <= moment()) && (!e.dueDate || e.dueDate > moment()));
                this.pastExercises = exercises.filter((e) => e.dueDate && e.dueDate <= moment());
            },
            (result: HttpErrorResponse) => this.jhiAlertService.error(result.message),
        );
        this.courseManagementService.getStatsForManagementOverview(this.course.id!).subscribe(
            (result: HttpResponse<CourseExerciseStatisticsDTO[]>) => result.body!.forEach((e) => (this.statistics[e.exerciseId!] = e)),
            (result: HttpErrorResponse) => this.jhiAlertService.error(result.message),
        );
    }

    getStatisticForExercise(exercise: Exercise) {
        const x = this.statistics[exercise.id!];
        console.log(x);
        return x;
    }
}

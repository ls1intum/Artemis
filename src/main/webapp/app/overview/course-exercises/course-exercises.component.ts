import { Component, OnInit } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { HttpResponse } from '@angular/common/http';
import * as moment from 'moment';

@Component({
    selector: 'jhi-course-exercises',
    templateUrl: './course-exercises.component.html',
    styleUrls: ['../course-overview.scss']
})
export class CourseExercisesComponent implements OnInit {
    public readonly DUE_DATE_ASC = 1;
    public readonly DUE_DATE_DESC = -1;
    private courseId: number;
    private subscription: Subscription;
    public course: Course;
    public weeklyIndexKeys: string[];
    public weeklyExercisesGrouped: object;

    constructor(
        private courseService: CourseService,
        private courseCalculationService: CourseScoreCalculationService,
        private courseServer: CourseService,
        private route: ActivatedRoute) {
    }

    ngOnInit() {
        this.subscription = this.route.parent.params.subscribe(params => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);
        if (this.course === undefined) {
            this.courseService.findAll().subscribe((res: HttpResponse<Course[]>) => {
                this.courseCalculationService.setCourses(res.body);
                this.course = this.courseCalculationService.getCourse(this.courseId);
            });
        }
        this.groupExercises(this.DUE_DATE_DESC);
    }

    public groupExercises(selectedOrder: number): void {
        this.weeklyExercisesGrouped = {};
        this.weeklyIndexKeys = [];
        const groupedExercises = {};
        const indexKeys: string[] = [];
        const courseExercises = [...this.course.exercises];
        const sortedExercises = courseExercises.sort((a, b) => selectedOrder * (a.dueDate.valueOf() - b.dueDate.valueOf()));
        sortedExercises.forEach(exercise => {
            const dateIndex = moment(exercise.dueDate).startOf('week').format('YYYY-MM-DD');
            if (!groupedExercises[dateIndex]) {
                indexKeys.push(dateIndex);
                groupedExercises[dateIndex] = {
                    label: `<b>${moment(exercise.dueDate).startOf('week').format('DD/MM/YYYY')}</b> - <b>${moment(exercise.dueDate).endOf('week').format('DD/MM/YYYY')}</b>`,
                    isCollapsed: exercise.dueDate.isBefore(moment(), 'week'),
                    isCurrentWeek: exercise.dueDate.isSame(moment(), 'week'),
                    exercises: []
                };
            }
            groupedExercises[dateIndex].exercises.push(exercise);
        });
        this.weeklyExercisesGrouped = groupedExercises;
        this.weeklyIndexKeys = indexKeys;
    }

}

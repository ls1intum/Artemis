import { Component, OnInit, OnDestroy } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';
import * as moment from 'moment';
import { Exercise } from 'app/entities/exercise';

@Component({
    selector: 'jhi-course-exercises',
    templateUrl: './course-exercises.component.html',
    styleUrls: ['../course-overview.scss']
})
export class CourseExercisesComponent implements OnInit, OnDestroy {
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
        private translateService: TranslateService,
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

        this.translateService.onLangChange.subscribe((event: LangChangeEvent) => {
            this.groupExercises(this.DUE_DATE_DESC);

        });
    }

    ngOnDestroy(): void {
        this.translateService.onLangChange.unsubscribe();
    }

    public groupExercises(selectedOrder: number): void {
        this.weeklyExercisesGrouped = {};
        this.weeklyIndexKeys = [];
        const groupedExercises = {};
        const indexKeys: string[] = [];
        const courseExercises = [...this.course.exercises];
        const sortedExercises = this.sortExercises(courseExercises, selectedOrder);
        const notAssociatedExercises: Exercise[] = [];
        sortedExercises.forEach(exercise => {
            const dateValue = exercise.dueDate ? exercise.dueDate : exercise.releaseDate;
            if (!dateValue) {
                notAssociatedExercises.push(exercise);
                return;
            }
            const dateIndex = dateValue ? moment(dateValue).startOf('week').format('YYYY-MM-DD') : 'NoDate';
            if (!groupedExercises[dateIndex]) {
                indexKeys.push(dateIndex);
                if (dateValue) {
                    groupedExercises[dateIndex] = {
                        label: `<b>${moment(dateValue).startOf('week').format('DD/MM/YYYY')}</b> - <b>${moment(dateValue).endOf('week').format('DD/MM/YYYY')}</b>`,
                        isCollapsed: dateValue.isBefore(moment(), 'week'),
                        isCurrentWeek: dateValue.isSame(moment(), 'week'),
                        exercises: []
                    };
                } else {
                    groupedExercises[dateIndex] = {
                        label: `No date associated`,
                        isCollapsed: false,
                        isCurrentWeek: false,
                        exercises: []
                    };
                }
            }
            groupedExercises[dateIndex].exercises.push(exercise);
        });
        this.weeklyExercisesGrouped = {
            ...groupedExercises,
            'noDate': {
                label: this.translateService.instant('arTeMiSApp.courseOverview.exerciseList.noExerciseDate'),
                isCollapsed: false,
                isCurrentWeek: false,
                exercises: notAssociatedExercises
            }
        };
        this.weeklyIndexKeys = [...indexKeys, 'noDate'];
    }

    private sortExercises(exercises: Exercise[], selectedOrder: number) {
        return exercises.sort((a, b) => {
            const aValue = a.dueDate ? a.dueDate.valueOf() : a.releaseDate ? a.releaseDate.valueOf() : moment().valueOf();
            const bValue = b.dueDate ? b.dueDate.valueOf() : a.releaseDate ? a.releaseDate.valueOf() : moment().valueOf();

            return selectedOrder * (aValue - bValue);
        });
    }

}

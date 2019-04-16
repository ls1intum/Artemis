import { Component, OnDestroy, OnInit } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';
import * as moment from 'moment';
import { Exercise, ExerciseService } from 'app/entities/exercise';
import { Lecture } from 'app/entities/lecture';

@Component({
    selector: 'jhi-course-lectures',
    templateUrl: './course-lectures.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseLecturesComponent implements OnInit, OnDestroy {
    public readonly DUE_DATE_ASC = 1;
    public readonly DUE_DATE_DESC = -1;
    private courseId: number;
    private paramSubscription: Subscription;
    private translateSubscription: Subscription;
    public course: Course;
    public weeklyIndexKeys: string[];
    public weeklyLecturesGrouped: object;

    public upcomingLectures: Lecture[];

    public exerciseCountMap: Map<string, number>;

    constructor(
        private courseService: CourseService,
        private courseCalculationService: CourseScoreCalculationService,
        private courseServer: CourseService,
        private translateService: TranslateService,
        private exerciseService: ExerciseService,
        private route: ActivatedRoute,
    ) {}

    ngOnInit() {
        this.exerciseCountMap = new Map<string, number>();
        this.paramSubscription = this.route.parent.params.subscribe(params => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);
        if (this.course === undefined) {
            this.courseService.findAll().subscribe((res: HttpResponse<Course[]>) => {
                this.courseCalculationService.setCourses(res.body);
                this.course = this.courseCalculationService.getCourse(this.courseId);
            });
        }
        this.groupLectures(this.DUE_DATE_DESC);

        this.translateSubscription = this.translateService.onLangChange.subscribe((event: LangChangeEvent) => {
            this.groupLectures(this.DUE_DATE_DESC);
        });
    }

    ngOnDestroy(): void {
        this.translateSubscription.unsubscribe();
        this.paramSubscription.unsubscribe();
    }

    public groupLectures(selectedOrder: number): void {
        this.weeklyLecturesGrouped = {};
        this.weeklyIndexKeys = [];
        const groupedLectures = {};
        const indexKeys: string[] = [];
        const courseLectures = [...this.course.lectures];
        const sortedLectures = this.sortLectures(courseLectures, selectedOrder);
        const notAssociatedLectures: Lecture[] = [];
        const upcomingLectures: Lecture[] = [];
        sortedLectures.forEach(lecture => {
            const dateValue = moment(lecture.startDate);
            if (!dateValue) {
                notAssociatedLectures.push(lecture);
                return;
            }
            const dateIndex = dateValue
                ? moment(dateValue)
                      .startOf('week')
                      .format('YYYY-MM-DD')
                : 'NoDate';
            if (!groupedLectures[dateIndex]) {
                indexKeys.push(dateIndex);
                if (dateValue) {
                    groupedLectures[dateIndex] = {
                        label: `<b>${moment(dateValue)
                            .startOf('week')
                            .format('DD/MM/YYYY')}</b> - <b>${moment(dateValue)
                            .endOf('week')
                            .format('DD/MM/YYYY')}</b>`,
                        isCollapsed: dateValue.isBefore(moment(), 'week'),
                        isCurrentWeek: dateValue.isSame(moment(), 'week'),
                        lectures: [],
                    };
                } else {
                    groupedLectures[dateIndex] = {
                        label: `No date associated`,
                        isCollapsed: false,
                        isCurrentWeek: false,
                        lectrues: [],
                    };
                }
            }
            groupedLectures[dateIndex].lectures.push(lecture);
            if (lecture.startDate && moment().isSameOrBefore(lecture.startDate, 'day')) {
                upcomingLectures.push(lecture);
            }
        });
        this.updateUpcomingLectures(upcomingLectures);
        if (notAssociatedLectures.length > 0) {
            this.weeklyLecturesGrouped = {
                ...groupedLectures,
                noDate: {
                    label: this.translateService.instant('arTeMiSApp.courseOverview.exerciseList.noExerciseDate'),
                    isCollapsed: false,
                    isCurrentWeek: false,
                    exercises: notAssociatedLectures,
                },
            };
            this.weeklyIndexKeys = [...indexKeys, 'noDate'];
        } else {
            this.weeklyLecturesGrouped = groupedLectures;
            this.weeklyIndexKeys = indexKeys;
        }
    }

    private sortLectures(exercises: Lecture[], selectedOrder: number): Lecture[] {
        return exercises.sort((a, b) => {
            const aValue = a.startDate.valueOf();
            const bValue = b.startDate.valueOf();

            return selectedOrder * (aValue - bValue);
        });
    }

    private increaseExerciseCounter(exercise: Exercise) {
        if (!this.exerciseCountMap.has(exercise.type)) {
            this.exerciseCountMap.set(exercise.type, 1);
        } else {
            let exerciseCount = this.exerciseCountMap.get(exercise.type);
            this.exerciseCountMap.set(exercise.type, ++exerciseCount);
        }
    }

    private updateUpcomingLectures(upcomingLectures: Lecture[]) {
        if (upcomingLectures.length < 5) {
            this.upcomingLectures = this.sortLectures(upcomingLectures, this.DUE_DATE_ASC);
        } else {
            const numberOfExercises = upcomingLectures.length;
            upcomingLectures = upcomingLectures.slice(numberOfExercises - 5, numberOfExercises);
            this.upcomingLectures = this.sortLectures(upcomingLectures, this.DUE_DATE_ASC);
        }
    }

    get nextRelevantExercise(): Exercise {
        return this.exerciseService.getNextExerciseForHours(this.course.exercises);
    }
}

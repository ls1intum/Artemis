import { Component, OnDestroy, OnInit } from '@angular/core';
import { Course } from 'app/entities/course';
import { CourseService } from 'app/entities/course/course.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';
import * as moment from 'moment';
import { ExerciseService } from 'app/entities/exercise';
import { Lecture } from 'app/entities/lecture';
import { CourseScoreCalculationService } from 'app/overview';

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
    public course: Course | null;
    public weeklyIndexKeys: string[];
    public weeklyLecturesGrouped: object;

    public exerciseCountMap: Map<string, number>;
    public totalAttachmentCount: number;

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
        this.paramSubscription = this.route.parent!.params.subscribe(params => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);
        if (this.course === undefined) {
            this.courseService.findAll().subscribe((res: HttpResponse<Course[]>) => {
                this.courseCalculationService.setCourses(res.body!);
                this.course = this.courseCalculationService.getCourse(this.courseId);
            });
        }
        this.groupLectures(this.DUE_DATE_DESC);

        this.translateSubscription = this.translateService.onLangChange.subscribe((event: LangChangeEvent) => {
            this.groupLectures(this.DUE_DATE_DESC);
        });

        this.totalAttachmentCount = this.getAttachmentCount();
    }

    ngOnDestroy(): void {
        if (this.translateService) {
            this.translateSubscription.unsubscribe();
        }
        if (this.paramSubscription) {
            this.paramSubscription.unsubscribe();
        }
    }

    public groupLectures(selectedOrder: number): void {
        this.weeklyLecturesGrouped = {};
        this.weeklyIndexKeys = [];
        const groupedLectures = {};
        const indexKeys: string[] = [];
        const courseLectures = this.course!.lectures ? [...this.course!.lectures] : [];
        const sortedLectures = this.sortLectures(courseLectures, selectedOrder);
        const notAssociatedLectures: Lecture[] = [];
        sortedLectures.forEach(lecture => {
            const dateValue = lecture.startDate ? moment(lecture.startDate) : null;
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
                        lectures: [],
                    };
                }
            }
            groupedLectures[dateIndex].lectures.push(lecture);
        });
        if (notAssociatedLectures.length > 0) {
            this.weeklyLecturesGrouped = {
                ...groupedLectures,
                noDate: {
                    label: this.translateService.instant('artemisApp.courseOverview.exerciseList.noExerciseDate'),
                    isCollapsed: false,
                    isCurrentWeek: false,
                    lectures: notAssociatedLectures,
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
            const aValue = a.startDate ? a.startDate.valueOf() : moment().valueOf();
            const bValue = b.startDate ? b.startDate.valueOf() : moment().valueOf();

            return selectedOrder * (aValue - bValue);
        });
    }

    private getAttachmentCount() {
        return this.course && this.course.lectures ? this.course.lectures.reduce((prev, el) => prev + (el.attachments ? el.attachments.length : 0), 0) : 0;
    }
}

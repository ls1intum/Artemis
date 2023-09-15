import { AfterViewInit, Component, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { Lecture } from 'app/entities/lecture.model';
import { LocalStorageService } from 'ngx-webstorage';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { faAngleDown, faAngleUp, faSortNumericDown, faSortNumericUp } from '@fortawesome/free-solid-svg-icons';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/shared/tab-bar/tab-bar';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

export enum LectureSortingOrder {
    ASC = 1,
    DESC = -1,
}

enum SortFilterStorageKey {
    ORDER = 'artemis.course.lectures.order',
}

@Component({
    selector: 'jhi-course-lectures',
    templateUrl: './course-lectures.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseLecturesComponent implements OnInit, OnDestroy, AfterViewInit, BarControlConfigurationProvider {
    private courseId: number;
    private paramSubscription: Subscription;
    private courseUpdatesSubscription: Subscription;
    private translateSubscription: Subscription;
    public course?: Course;
    public weeklyIndexKeys: string[];
    public weeklyLecturesGrouped: object;

    public exerciseCountMap: Map<string, number>;

    readonly ASC = LectureSortingOrder.ASC;
    readonly DESC = LectureSortingOrder.DESC;

    // Icons
    faSortNumericDown = faSortNumericDown;
    faSortNumericUp = faSortNumericUp;
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    sortingOrder: LectureSortingOrder;

    // The extracted controls template from our template to be rendered in the top bar of "CourseOverviewComponent"
    @ViewChild('controls', { static: false }) private controls: TemplateRef<any>;
    // Provides the control configuration to be read and used by "CourseOverviewComponent"
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
    };

    constructor(
        private courseStorageService: CourseStorageService,
        private translateService: TranslateService,
        private exerciseService: ExerciseService,
        private route: ActivatedRoute,
        private localStorage: LocalStorageService,
    ) {}

    ngOnInit() {
        this.exerciseCountMap = new Map<string, number>();
        this.loadSortingOrder();
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseStorageService.getCourse(this.courseId);
        this.onCourseLoad();

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.course = course;
            this.onCourseLoad();
        });

        this.translateSubscription = this.translateService.onLangChange.subscribe(() => {
            this.groupLectures();
        });
    }

    ngAfterViewInit(): void {
        // Send our controls template to parent so it will be rendered in the top bar
        if (this.controls) {
            this.controlConfiguration.subject!.next(this.controls);
        }
    }

    ngOnDestroy(): void {
        this.translateSubscription.unsubscribe();
        this.courseUpdatesSubscription.unsubscribe();
        this.paramSubscription.unsubscribe();
    }

    /**
     * Loads the sorting order from local storage
     */
    private loadSortingOrder() {
        const orderInStorage = this.localStorage.retrieve(SortFilterStorageKey.ORDER);
        const parsedOrderInStorage = Object.keys(LectureSortingOrder).find((exerciseOrder) => exerciseOrder === orderInStorage);
        this.sortingOrder = parsedOrderInStorage ? (+parsedOrderInStorage as LectureSortingOrder) : LectureSortingOrder.ASC;
    }

    private onCourseLoad() {
        this.groupLectures();
    }

    /**
     * Reorders all displayed lectures
     */
    flipOrder() {
        this.sortingOrder = this.sortingOrder === this.ASC ? this.DESC : this.ASC;
        this.localStorage.store(SortFilterStorageKey.ORDER, this.sortingOrder.toString());
        this.groupLectures();
    }

    public groupLectures(): void {
        this.weeklyLecturesGrouped = {};
        this.weeklyIndexKeys = [];
        const groupedLectures = {};
        const indexKeys: string[] = [];
        const courseLectures = this.course?.lectures ? [...this.course!.lectures] : [];
        const sortedLectures = this.sortLectures(courseLectures, this.sortingOrder);
        const notAssociatedLectures: Lecture[] = [];
        sortedLectures.forEach((lecture) => {
            const dateValue = lecture.startDate ? dayjs(lecture.startDate) : undefined;
            if (!dateValue) {
                notAssociatedLectures.push(lecture);
                return;
            }
            const dateIndex = dateValue ? dayjs(dateValue).startOf('week').format('YYYY-MM-DD') : 'NoDate';
            if (!groupedLectures[dateIndex]) {
                indexKeys.push(dateIndex);
                if (dateValue) {
                    groupedLectures[dateIndex] = {
                        start: dayjs(dateValue).startOf('week'),
                        end: dayjs(dateValue).endOf('week'),
                        isCollapsed: dateValue.isBefore(dayjs(), 'week') || dateValue.isAfter(dayjs(), 'week'),
                        isCurrentWeek: dateValue.isSame(dayjs(), 'week'),
                        lectures: [],
                    };
                } else {
                    groupedLectures[dateIndex] = {
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

    private sortLectures(lectures: Lecture[], selectedOrder: number): Lecture[] {
        return lectures.sort((a, b) => {
            const aValue = a.startDate ? a.startDate.valueOf() : dayjs().valueOf();
            const bValue = b.startDate ? b.startDate.valueOf() : dayjs().valueOf();

            return selectedOrder * (aValue - bValue);
        });
    }
}

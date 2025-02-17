import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CoursesForDashboardDTO } from 'app/course/manage/courses-for-dashboard-dto';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { courseOverviewTour } from 'app/guided-tour/tours/course-overview-tour';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { WebsocketService } from 'app/core/websocket/websocket.service';
import dayjs from 'dayjs/esm';
import { Exam } from 'app/entities/exam/exam.model';
import { Router, RouterLink } from '@angular/router';
import { faArrowDownAZ, faArrowUpAZ, faDoorOpen, faPenAlt } from '@fortawesome/free-solid-svg-icons';
import { CourseAccessStorageService } from 'app/course/course-access-storage.service';
import { CourseForDashboardDTO } from 'app/course/manage/course-for-dashboard-dto';
import { sortCourses } from 'app/shared/util/course.util';
import { TranslateDirective } from '../shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SearchFilterComponent } from '../shared/search-filter/search-filter.component';
import { NgTemplateOutlet } from '@angular/common';
import { CourseCardComponent } from './course-card.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from '../shared/pipes/artemis-translate.pipe';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';

@Component({
    selector: 'jhi-overview',
    templateUrl: './courses.component.html',
    styleUrls: ['./courses.component.scss'],
    imports: [
        TranslateDirective,
        FaIconComponent,
        SearchFilterComponent,
        RouterLink,
        NgTemplateOutlet,
        CourseCardComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        SearchFilterPipe,
    ],
})
export class CoursesComponent implements OnInit, OnDestroy {
    private courseService = inject(CourseManagementService);
    private guidedTourService = inject(GuidedTourService);
    private teamService = inject(TeamService);
    private websocketService = inject(WebsocketService);
    private router = inject(Router);
    private courseAccessStorageService = inject(CourseAccessStorageService);

    protected readonly faPenAlt = faPenAlt;
    protected readonly faArrowDownAZ = faArrowDownAZ;
    protected readonly faArrowUpAZ = faArrowUpAZ;
    protected readonly faDoorOpen = faDoorOpen;

    courses: Course[];
    public nextRelevantCourse?: Course;
    nextRelevantCourseForExam?: Course;
    nextRelevantExams?: Exam[];

    public recentlyAccessedCourses: Course[] = [];
    public regularCourses: Course[] = [];

    courseForGuidedTour?: Course;
    quizExercisesChannels: string[] = [];
    searchCourseText = '';

    coursesLoaded = false;
    isSortAscending = true;

    async ngOnInit() {
        this.loadAndFilterCourses();
        (await this.teamService.teamAssignmentUpdates).subscribe();
        this.courseService.enableCourseOverviewBackground();
    }

    /**
     * Unsubscribe from all websocket subscriptions.
     */
    ngOnDestroy() {
        if (this.quizExercisesChannels) {
            this.quizExercisesChannels.forEach((channel) => this.websocketService.unsubscribe(channel));
        }
        this.courseService.disableCourseOverviewBackground();
    }

    loadAndFilterCourses() {
        this.courseService.findAllForDashboard().subscribe({
            next: (res: HttpResponse<CoursesForDashboardDTO>) => {
                if (res.body) {
                    this.coursesLoaded = true;
                    const courses: Course[] = [];
                    if (res.body.courses === undefined || res.body.courses.length === 0) {
                        return;
                    }
                    res.body.courses.forEach((courseDto: CourseForDashboardDTO) => {
                        courses.push(courseDto.course);
                    });
                    this.courses = sortCourses(courses);
                    this.courseForGuidedTour = this.guidedTourService.enableTourForCourseOverview(this.courses, courseOverviewTour, true);

                    this.nextRelevantExams = res.body.activeExams ?? [];
                    this.sortCoursesInRecentlyAccessedAndRegularCourses();
                }
            },
        });
    }

    /**
     * Sorts the courses into recently accessed and regular courses.
     * If there are less than 5 courses, all courses are displayed in the regular courses section.
     */
    sortCoursesInRecentlyAccessedAndRegularCourses() {
        if (this.courses.length <= 5) {
            this.regularCourses = this.courses;
        } else {
            const lastAccessedCourseIds = this.courseAccessStorageService.getLastAccessedCourses(CourseAccessStorageService.STORAGE_KEY);
            this.recentlyAccessedCourses = this.courses.filter((course) => lastAccessedCourseIds.includes(course.id!));
            this.regularCourses = this.courses.filter((course) => !lastAccessedCourseIds.includes(course.id!));
        }
    }

    /**
     * Sets the course for the next upcoming exam and returns the next upcoming exam or undefined
     */
    get nextRelevantExam(): Exam | undefined {
        // TODO: support multiple relevant exams in the future
        let relevantExam: Exam | undefined;
        if (this.nextRelevantExams) {
            if (this.nextRelevantExams.length === 0) {
                return undefined;
            } else if (this.nextRelevantExams.length === 1) {
                relevantExam = this.nextRelevantExams[0];
            } else {
                relevantExam = this.nextRelevantExams.sort((a, b) => {
                    return dayjs(a.startDate).valueOf() - dayjs(b.startDate).valueOf();
                })[0];
            }
            this.nextRelevantCourseForExam = relevantExam.course!;
        }
        return relevantExam;
    }

    /**
     * navigate to /courses/:courseid/exams/:examId
     */
    openExam(): void {
        this.router.navigate(['courses', this.nextRelevantCourseForExam?.id, 'exams', this.nextRelevantExam!.id]);
    }

    setSearchValue(searchValue: string): void {
        this.searchCourseText = searchValue;
    }

    /**
     * Sorts the courses in alphabetical order
     */
    onSort(): void {
        if (this.courses) {
            this.isSortAscending = !this.isSortAscending;
            this.regularCourses = [...sortCourses(this.regularCourses, this.isSortAscending)];
            this.recentlyAccessedCourses = [...sortCourses(this.recentlyAccessedCourses, this.isSortAscending)];
        }
    }
}

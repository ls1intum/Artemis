import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CoursesForDashboardDTO } from 'app/core/course/shared/entities/courses-for-dashboard-dto';
import { Course } from 'app/core/course/shared/entities/course.model';
import { HttpResponse } from '@angular/common/http';
import { TeamService } from 'app/exercise/team/team.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import dayjs from 'dayjs/esm';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Router, RouterLink } from '@angular/router';
import { faArrowDownAZ, faArrowUpAZ, faDoorOpen, faPenAlt } from '@fortawesome/free-solid-svg-icons';
import { CourseForDashboardDTO } from 'app/core/course/shared/entities/course-for-dashboard-dto';
import { sortCourses } from 'app/shared/util/course.util';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgTemplateOutlet } from '@angular/common';
import { CourseCardComponent } from '../course-card/course-card.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseAccessStorageService } from 'app/core/course/shared/services/course-access-storage.service';
import { addPublicFilePrefix } from 'app/app.constants';

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
    protected readonly faPenAlt = faPenAlt;
    protected readonly faArrowDownAZ = faArrowDownAZ;
    protected readonly faArrowUpAZ = faArrowUpAZ;
    protected readonly faDoorOpen = faDoorOpen;

    private courseService = inject(CourseManagementService);
    private teamService = inject(TeamService);
    private websocketService = inject(WebsocketService);
    private router = inject(Router);
    private courseAccessStorageService = inject(CourseAccessStorageService);

    courses: Course[];
    public nextRelevantCourse?: Course;
    nextRelevantCourseForExam?: Course;
    nextRelevantExams?: Exam[];

    public recentlyAccessedCourses: Course[] = [];
    public regularCourses: Course[] = [];

    quizExercisesChannels: string[] = [];
    searchCourseText = '';

    coursesLoaded = false;
    isSortAscending = true;

    async ngOnInit() {
        this.loadAndFilterCourses();
        (await this.teamService.teamAssignmentUpdates).subscribe();
    }

    /**
     * Unsubscribe from all websocket subscriptions.
     */
    ngOnDestroy() {
        if (this.quizExercisesChannels) {
            this.quizExercisesChannels.forEach((channel) => this.websocketService.unsubscribe(channel));
        }
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
                        courseDto.course.courseIconPath = addPublicFilePrefix(courseDto.course.courseIcon);
                        courses.push(courseDto.course);
                    });
                    this.courses = sortCourses(courses);

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

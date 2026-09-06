import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CoursesForDashboardDTO } from 'app/course/shared/entities/courses-for-dashboard-dto';
import { Course } from 'app/course/shared/entities/course.model';
import { HttpResponse } from '@angular/common/http';
import { TeamService } from 'app/exercise/team/team.service';
import dayjs from 'dayjs/esm';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Router, RouterLink } from '@angular/router';
import { faArrowDownAZ, faArrowUpAZ, faBook, faDoorOpen, faPenAlt, faPlus } from '@fortawesome/free-solid-svg-icons';
import { CourseForDashboardDTO } from 'app/course/shared/entities/course-for-dashboard-dto';
import { sortCourses } from 'app/foundation/util/course.util';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgTemplateOutlet } from '@angular/common';
import { CourseCardComponent } from '../course-card/course-card.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { SearchFilterPipe } from 'app/foundation/pipes/search-filter.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseAccessStorageService } from 'app/course/shared/services/course-access-storage.service';
import { addPublicFilePrefix } from 'app/app.constants';
import { AccountService } from 'app/core/auth/account.service';
import { IS_AT_LEAST_INSTRUCTOR } from 'app/foundation/constants/authority.constants';
import { TumUiButtonDirective } from '@tumaet/ui-angular';

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
        TumUiButtonDirective,
    ],
})
export class CoursesComponent implements OnInit {
    protected readonly faPenAlt = faPenAlt;
    protected readonly faArrowDownAZ = faArrowDownAZ;
    protected readonly faArrowUpAZ = faArrowUpAZ;
    protected readonly faDoorOpen = faDoorOpen;
    protected readonly faBook = faBook;
    protected readonly faPlus = faPlus;

    private courseService = inject(CourseManagementService);
    private teamService = inject(TeamService);
    private router = inject(Router);
    private courseAccessStorageService = inject(CourseAccessStorageService);
    private accountService = inject(AccountService);

    // All written inside the dashboard HTTP subscribe — must be signals under zoneless,
    // otherwise the course list silently never renders after the response arrives.
    readonly courses = signal<Course[]>(undefined!);
    public nextRelevantCourse?: Course;
    readonly nextRelevantExams = signal<Exam[] | undefined>(undefined);

    /**
     * The next upcoming exam, derived from {@link nextRelevantExams} (the one with the earliest start date).
     */
    readonly nextRelevantExam = computed<Exam | undefined>(() => {
        // TODO: support multiple relevant exams in the future
        const nextRelevantExams = this.nextRelevantExams();
        if (!nextRelevantExams?.length) {
            return undefined;
        }
        if (nextRelevantExams.length === 1) {
            return nextRelevantExams[0];
        }
        return [...nextRelevantExams].sort((a, b) => dayjs(a.startDate).valueOf() - dayjs(b.startDate).valueOf())[0];
    });

    readonly nextRelevantCourseForExam = computed<Course | undefined>(() => this.nextRelevantExam()?.course);

    public readonly recentlyAccessedCourses = signal<Course[]>([]);
    public readonly regularCourses = signal<Course[]>([]);
    public readonly testCourses = signal<Course[]>([]);

    protected readonly isAdmin = computed(() => this.accountService.isAdmin());
    protected readonly canRequestCourse = computed(() => !this.isAdmin() && this.accountService.hasAnyAuthorityDirect(IS_AT_LEAST_INSTRUCTOR));

    readonly searchCourseText = signal('');

    readonly coursesLoaded = signal(false);
    readonly isSortAscending = signal(true);

    async ngOnInit() {
        this.loadAndFilterCourses();
        (await this.teamService.teamAssignmentUpdates).subscribe();
    }

    loadAndFilterCourses() {
        this.courseService.findAllForDashboard().subscribe({
            next: (res: HttpResponse<CoursesForDashboardDTO>) => {
                if (res.body) {
                    this.coursesLoaded.set(true);
                    const courses: Course[] = [];
                    (res.body.courses ?? []).forEach((courseDto: CourseForDashboardDTO) => {
                        courseDto.course.courseIconPath = addPublicFilePrefix(courseDto.course.courseIcon);
                        courses.push(courseDto.course);
                    });
                    this.courses.set(sortCourses(courses));

                    this.nextRelevantExams.set(res.body.activeExams ?? []);
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
        const courses = this.courses().filter((course) => !course.testCourse);
        this.testCourses.set(this.courses().filter((course) => course.testCourse));
        if (courses.length <= 5) {
            this.recentlyAccessedCourses.set([]);
            this.regularCourses.set(courses);
        } else {
            const lastAccessedCourseIds = this.courseAccessStorageService.getLastAccessedCourses(CourseAccessStorageService.STORAGE_KEY);
            this.recentlyAccessedCourses.set(courses.filter((course) => lastAccessedCourseIds.includes(course.id!)));
            this.regularCourses.set(courses.filter((course) => !lastAccessedCourseIds.includes(course.id!)));
        }
    }

    /**
     * navigate to /courses/:courseid/exams/:examId
     */
    openExam(): void {
        void this.router.navigate(['courses', this.nextRelevantCourseForExam()?.id, 'exams', this.nextRelevantExam()!.id]);
    }

    setSearchValue(searchValue: string): void {
        this.searchCourseText.set(searchValue);
    }

    /**
     * Types the implicit context value of the shared course-card template. The `*ngTemplateOutlet` context is
     * always populated with a `Course[]` (either {@link recentlyAccessedCourses} or {@link regularCourses}), but
     * the template compiler widens template `let-` variables to `unknown`; this narrows it back in one safe place.
     */
    asCourses(courses: unknown): Course[] {
        return courses as Course[];
    }

    /**
     * Sorts the courses in alphabetical order
     */
    onSort(): void {
        if (this.courses()) {
            this.isSortAscending.update((value) => !value);
            this.regularCourses.set([...sortCourses(this.regularCourses(), this.isSortAscending())]);
            this.recentlyAccessedCourses.set([...sortCourses(this.recentlyAccessedCourses(), this.isSortAscending())]);
            this.testCourses.set([...sortCourses(this.testCourses(), this.isSortAscending())]);
        }
    }
}

import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { CourseArchiveComponent } from 'app/overview/course-archive/course-archive.component';
import { CourseCardHeaderComponent } from 'app/overview/course-card-header/course-card-header.component';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { CourseForArchiveDTO } from 'app/course/manage/course-for-archive-dto';

const course1 = { id: 1, semester: 'WS21/22', title: 'iPraktikum' } as CourseForArchiveDTO;
const course2 = { id: 2, semester: 'WS21/22' } as CourseForArchiveDTO;
const course3 = { id: 3, semester: 'SS22' } as CourseForArchiveDTO;
const course4 = { id: 4, semester: 'SS22' } as CourseForArchiveDTO;
const course5 = { id: 5, semester: 'WS23/24' } as CourseForArchiveDTO;
const course6 = { id: 6, semester: 'SS19' } as CourseForArchiveDTO;
const course7 = { id: 7, semester: 'WS22/23' } as CourseForArchiveDTO;
const courses: CourseForArchiveDTO[] = [course1, course2, course3, course4, course5, course6, course7];

describe('CourseArchiveComponent', () => {
    let component: CourseArchiveComponent;
    let fixture: ComponentFixture<CourseArchiveComponent>;
    let courseService: CourseManagementService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseArchiveComponent,
                SearchFilterPipe,
                SearchFilterComponent,
                MockDirective(MockHasAnyAuthorityDirective),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseCardHeaderComponent),
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseArchiveComponent);
                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
                httpMock = TestBed.inject(HttpTestingController);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        component.ngOnDestroy();
        jest.restoreAllMocks();
    });

    describe('onInit', () => {
        it('should call loadArchivedCourses on init', () => {
            const loadArchivedCoursesSpy = jest.spyOn(component, 'loadArchivedCourses');

            component.ngOnInit();

            expect(loadArchivedCoursesSpy).toHaveBeenCalledOnce();
        });

        it('should load archived courses on init', () => {
            const getCoursesForArchiveSpy = jest.spyOn(courseService, 'getCoursesForArchive');
            getCoursesForArchiveSpy.mockReturnValue(of(new HttpResponse({ body: courses, headers: new HttpHeaders() })));

            component.ngOnInit();

            expect(component.courses).toEqual(courses);
            expect(component.courses).toHaveLength(7);
        });

        it('should handle an empty response body correctly when fetching all courses for archive', () => {
            const emptyCourses: CourseForArchiveDTO[] = [];
            const getCoursesForArchiveSpy = jest.spyOn(courseService, 'getCoursesForArchive');

            const req = httpMock.expectOne({ method: 'GET', url: `api/courses/for-archive` });
            component.ngOnInit();

            expect(getCoursesForArchiveSpy).toHaveBeenCalledOnce();
            req.flush(null);
            expect(component.courses).toStrictEqual(emptyCourses);
        });

        it('should sort the name of the semesters uniquely', () => {
            const getCoursesForArchiveSpy = jest.spyOn(courseService, 'getCoursesForArchive');
            getCoursesForArchiveSpy.mockReturnValue(of(new HttpResponse({ body: courses, headers: new HttpHeaders() })));
            component.ngOnInit();

            expect(getCoursesForArchiveSpy).toHaveBeenCalledOnce();

            expect(component.semesters).toHaveLength(5);
            expect(component.semesters[0]).toBe('WS23/24');
            expect(component.semesters[1]).toBe('WS22/23');
            expect(component.semesters[2]).toBe('SS22');
            expect(component.semesters[3]).toBe('WS21/22');
            expect(component.semesters[4]).toBe('SS19');
        });

        it('should map courses into semesters', () => {
            const getCoursesForArchiveSpy = jest.spyOn(courseService, 'getCoursesForArchive');
            getCoursesForArchiveSpy.mockReturnValue(of(new HttpResponse({ body: courses, headers: new HttpHeaders() })));
            const mapCoursesIntoSemestersSpy = jest.spyOn(component, 'mapCoursesIntoSemesters');
            component.ngOnInit();

            expect(getCoursesForArchiveSpy).toHaveBeenCalledOnce();
            expect(mapCoursesIntoSemestersSpy).toHaveBeenCalledOnce();

            expect(component.coursesBySemester).toStrictEqual({
                'WS23/24': [course5],
                'WS22/23': [course7],
                SS22: [course3, course4],
                'WS21/22': [course2, course1],
                SS19: [course6],
            });
        });

        it('should initialize collapse state of semesters correctly', () => {
            const getCoursesForArchiveSpy = jest.spyOn(courseService, 'getCoursesForArchive');
            getCoursesForArchiveSpy.mockReturnValue(of(new HttpResponse({ body: courses, headers: new HttpHeaders() })));
            const mapCoursesIntoSemestersSpy = jest.spyOn(component, 'mapCoursesIntoSemesters');
            component.ngOnInit();

            expect(getCoursesForArchiveSpy).toHaveBeenCalledOnce();
            expect(mapCoursesIntoSemestersSpy).toHaveBeenCalledOnce();

            // we expand all semesters at first
            expect(component.semesterCollapsed).toStrictEqual({
                'WS23/24': false,
                'WS22/23': false,
                SS22: false,
                'WS21/22': false,
                SS19: false,
            });
        });

        it('should initialize translate of semesters correctly', () => {
            const getCoursesForArchiveSpy = jest.spyOn(courseService, 'getCoursesForArchive');
            getCoursesForArchiveSpy.mockReturnValue(of(new HttpResponse({ body: courses, headers: new HttpHeaders() })));
            const mapCoursesIntoSemestersSpy = jest.spyOn(component, 'mapCoursesIntoSemesters');
            component.ngOnInit();

            expect(getCoursesForArchiveSpy).toHaveBeenCalledOnce();
            expect(mapCoursesIntoSemestersSpy).toHaveBeenCalledOnce();

            expect(component.fullFormOfSemesterStrings).toStrictEqual({
                'WS23/24': 'artemisApp.course.archive.winterSemester',
                'WS22/23': 'artemisApp.course.archive.winterSemester',
                SS22: 'artemisApp.course.archive.summerSemester',
                'WS21/22': 'artemisApp.course.archive.winterSemester',
                SS19: 'artemisApp.course.archive.summerSemester',
            });
        });

        it('should collapse semester groups based on the search value correctly', () => {
            const getCoursesForArchiveSpy = jest.spyOn(courseService, 'getCoursesForArchive');
            getCoursesForArchiveSpy.mockReturnValue(of(new HttpResponse({ body: courses, headers: new HttpHeaders() })));
            const mapCoursesIntoSemestersSpy = jest.spyOn(component, 'mapCoursesIntoSemesters');
            component.ngOnInit();

            expect(getCoursesForArchiveSpy).toHaveBeenCalledOnce();
            expect(mapCoursesIntoSemestersSpy).toHaveBeenCalledOnce();

            const expandOrCollapseBasedOnSearchValueSpy = jest.spyOn(component, 'expandOrCollapseBasedOnSearchValue');
            component.setSearchValue('iPraktikum');

            expect(expandOrCollapseBasedOnSearchValueSpy).toHaveBeenCalledOnce();
            // Every semester accordion should be collapsed except WS21/22, because iPraktikum is in semester WS21/22
            expect(component.semesterCollapsed).toStrictEqual({
                'WS23/24': true,
                'WS22/23': true,
                SS22: true,
                'WS21/22': false,
                SS19: true,
            });
        });

        it('should toggle sort order and update the icon accordingly', fakeAsync(() => {
            const getCoursesForArchiveSpy = jest.spyOn(courseService, 'getCoursesForArchive');
            getCoursesForArchiveSpy.mockReturnValue(of(new HttpResponse({ body: courses, headers: new HttpHeaders() })));
            const mapCoursesIntoSemestersSpy = jest.spyOn(component, 'mapCoursesIntoSemesters');
            component.ngOnInit();
            fixture.detectChanges();
            tick();

            expect(getCoursesForArchiveSpy).toHaveBeenCalledOnce();
            expect(mapCoursesIntoSemestersSpy).toHaveBeenCalledOnce();
            expect(component.courses).toBeDefined();
            expect(component.courses).toHaveLength(7);

            const onSortSpy = jest.spyOn(component, 'onSort');
            const button = fixture.debugElement.nativeElement.querySelector('#sort-test');

            expect(button).not.toBeNull();
            button.click();
            fixture.detectChanges();

            expect(onSortSpy).toHaveBeenCalled();
            expect(component.isSortAscending).toBeFalse();
            expect(component.semesters[4]).toBe('WS23/24');
            expect(component.semesters[3]).toBe('WS22/23');
            expect(component.semesters[2]).toBe('SS22');
            expect(component.semesters[1]).toBe('WS21/22');
            expect(component.semesters[0]).toBe('SS19');

            const iconComponent = fixture.debugElement.query(By.css('#icon-test-down')).componentInstance;

            expect(iconComponent).not.toBeNull();
            expect(iconComponent.icon).toBe(component.faArrowUp19);
        }));

        it('should find the correct course and call toggle', fakeAsync(() => {
            const getCoursesForArchiveSpy = jest.spyOn(courseService, 'getCoursesForArchive');
            getCoursesForArchiveSpy.mockReturnValue(of(new HttpResponse({ body: courses, headers: new HttpHeaders() })));
            const mapCoursesIntoSemestersSpy = jest.spyOn(component, 'mapCoursesIntoSemesters');

            component.ngOnInit();
            tick();
            fixture.detectChanges();
            expect(component.courses).toHaveLength(7);
            expect(getCoursesForArchiveSpy).toHaveBeenCalledOnce();
            expect(mapCoursesIntoSemestersSpy).toHaveBeenCalledOnce();

            // iPraktikum is in semester-group-3 : WS21/22
            const button = fixture.debugElement.nativeElement.querySelector('#semester-group-3');
            const toggleCollapseStateSpy = jest.spyOn(component, 'toggleCollapseState');
            component.setSearchValue('iPraktikum');
            const courseFound = component.isCourseFoundInSemester('WS21/22');
            expect(courseFound).toBeTrue();
            expect(button).not.toBeNull();
            button.click();
            expect(toggleCollapseStateSpy).toHaveBeenCalledOnce();
        }));

        it('should initialize collapse state correctly', () => {
            const getCoursesForArchiveSpy = jest.spyOn(courseService, 'getCoursesForArchive');
            getCoursesForArchiveSpy.mockReturnValue(of(new HttpResponse({ body: courses, headers: new HttpHeaders() })));
            const mapCoursesIntoSemestersSpy = jest.spyOn(component, 'mapCoursesIntoSemesters');

            component.ngOnInit();
            expect(component.courses).toHaveLength(7);
            expect(getCoursesForArchiveSpy).toHaveBeenCalledOnce();
            expect(mapCoursesIntoSemestersSpy).toHaveBeenCalledOnce();
            const getCollapseStateForSemestersSpy = jest.spyOn(component, 'getCollapseStateForSemesters');
            component.setSearchValue('');
            expect(getCollapseStateForSemestersSpy).toHaveBeenCalledOnce();

            expect(component.semesterCollapsed).toStrictEqual({
                'WS23/24': false,
                'WS22/23': false,
                SS22: false,
                'WS21/22': false,
                SS19: false,
            });
        });
    });
});

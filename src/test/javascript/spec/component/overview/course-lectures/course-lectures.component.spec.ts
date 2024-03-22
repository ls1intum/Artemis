import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbDropdownModule, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService } from 'ngx-webstorage';
import { Course } from 'app/entities/course.model';
import { Lecture } from 'app/entities/lecture.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CourseLecturesComponent } from '../../../../../../main/webapp/app/overview/course-lectures/course-lectures.component';
import { SidebarComponent } from '../../../../../../main/webapp/app/shared/sidebar/sidebar.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { BehaviorSubject, of } from 'rxjs';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { CourseOverviewService } from '../../../../../../main/webapp/app/overview/course-overview.service';
import { HttpClientModule } from '@angular/common/http';
import { ProfileService } from '../../../../../../main/webapp/app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { ProfileInfo } from '../../../../../../main/webapp/app/shared/layouts/profiles/profile-info.model';
import { RouterTestingModule } from '@angular/router/testing';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

@Component({ selector: 'jhi-course-lecture-row', template: '' })
class CourseLectureRowStubComponent {
    @Input() lecture: Lecture;
    @Input() course: Course;
}

describe('CourseLectures', () => {
    let courseLecturesComponentFixture: ComponentFixture<CourseLecturesComponent>;
    let courseLecturesComponent: CourseLecturesComponent;
    let course: Course;
    let lecture1: Lecture;
    let lecture2: Lecture;
    let lecture3: Lecture;
    let profileService: ProfileService;

    let getProfileInfoMock: jest.SpyInstance;

    beforeEach(() => {
        course = new Course();
        course.id = 1;

        const wednesdayBase = dayjs('18-03-2020', 'DD-MM-YYYY');
        const wednesdayBefore = dayjs('11-03-2020', 'DD-MM-YYYY');
        const wednesdayAfter = dayjs('25-03-2020', 'DD-MM-YYYY');

        lecture1 = new Lecture();
        lecture1.id = 1;
        lecture1.startDate = wednesdayBase;
        lecture2 = new Lecture();
        lecture2.id = 2;
        lecture2.startDate = wednesdayBefore;
        lecture3 = new Lecture();
        lecture3.id = 3;
        lecture3.startDate = wednesdayAfter;
        course.lectures = [lecture1, lecture2, lecture3];

        TestBed.configureTestingModule({
            imports: [NgbDropdownModule, HttpClientModule, RouterTestingModule, MockModule(FormsModule), MockModule(ReactiveFormsModule), MockModule(NgbTooltipModule)],
            declarations: [
                CourseLecturesComponent,
                CourseLectureRowStubComponent,
                SidebarComponent,
                SearchFilterComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(SearchFilterPipe),
                MockComponent(SidePanelComponent),
                MockComponent(FaIconComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                MockProvider(CourseStorageService, {
                    getCourse: () => {
                        return course;
                    },
                    subscribeToCourseUpdates: () => {
                        return of(course);
                    },
                }),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ExerciseService),
                MockProvider(CourseOverviewService, {
                    getUpcomingLecture: () => {
                        return lecture3;
                    },
                    sortLectures: () => {
                        return [lecture1, lecture1, lecture3];
                    },
                }),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({ courseId: '1' }),
                        },
                        params: of({ lectureId: lecture1.id }),
                    },
                },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .compileComponents()
            .then(() => {
                courseLecturesComponentFixture = TestBed.createComponent(CourseLecturesComponent);
                courseLecturesComponent = courseLecturesComponentFixture.componentInstance;

                // mock profileService
                profileService = courseLecturesComponentFixture.debugElement.injector.get(ProfileService);
                getProfileInfoMock = jest.spyOn(profileService, 'getProfileInfo');
                const profileInfo = { inProduction: false } as ProfileInfo;
                const profileInfoSubject = new BehaviorSubject<ProfileInfo | null>(profileInfo);
                getProfileInfoMock.mockReturnValue(profileInfoSubject);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        courseLecturesComponentFixture.detectChanges();
        expect(courseLecturesComponent).not.toBeNull();
        courseLecturesComponent.ngOnDestroy();
    });

    it('should sort correctly', fakeAsync(() => {
        courseLecturesComponentFixture.detectChanges();

        // check if the lectures are sorted correctly inside the groups
        const lectures = courseLecturesComponent.sortedLectures;
        for (let i = 0; i < lectures.length - 1; i++) {
            const firstLecture = lectures[i];
            const secondLecture = lectures[i + 1];
            const firstStartDate = firstLecture.startDate ? firstLecture.startDate.valueOf() : dayjs().valueOf();
            const secondStartDate = secondLecture.startDate ? secondLecture.startDate.valueOf() : dayjs().valueOf();

            expect(firstStartDate).toBeLessThanOrEqual(secondStartDate);
        }
    }));
});

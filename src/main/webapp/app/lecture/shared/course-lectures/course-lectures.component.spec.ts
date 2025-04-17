import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { CourseLecturesComponent } from 'app/lecture/shared/course-lectures/course-lectures.component';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { CourseOverviewService } from 'app/core/course/overview/services/course-overview.service';
import { HttpResponse } from '@angular/common/http';
import { LtiService } from 'app/shared/service/lti.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { RouterTestingModule } from '@angular/router/testing';

describe('CourseLecturesComponent', () => {
    let component: CourseLecturesComponent;
    let fixture: ComponentFixture<CourseLecturesComponent>;
    let ltiService: LtiService;
    let courseOverviewService: CourseOverviewService;
    let lectureService: LectureService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule, CourseLecturesComponent],
            declarations: [],
            providers: [
                LtiService,
                MockProvider(CourseStorageService, {
                    getCourse: () => ({ id: 1, lectures: [] }) as Course,
                    subscribeToCourseUpdates: () => of({} as Course),
                }),
                MockProvider(CourseOverviewService),
                MockProvider(LectureService, {
                    find: () => of(new HttpResponse({ body: new Lecture() })),
                }),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({ courseId: '1' }),
                        },
                        firstChild: {
                            snapshot: {
                                params: {},
                            },
                        },
                        queryParams: of({}),
                    },
                },
            ],
        });

        fixture = TestBed.createComponent(CourseLecturesComponent);
        component = fixture.componentInstance;
        ltiService = TestBed.inject(LtiService);
        courseOverviewService = TestBed.inject(CourseOverviewService);
        lectureService = TestBed.inject(LectureService);
    });

    it('should handle multi-launch subscription', fakeAsync(() => {
        const processSpy = jest.spyOn(component, 'processLectures');
        const sortSpy = jest.spyOn(courseOverviewService, 'sortLectures').mockReturnValue([]);
        const mapSpy = jest.spyOn(courseOverviewService, 'mapLecturesToSidebarCardElements').mockReturnValue([]);

        ltiService.setMultiLaunch(true);
        component.ngOnInit();

        expect(component.isMultiLaunch).toBeTrue();

        ltiService.setMultiLaunch(false);

        expect(component.isMultiLaunch).toBeFalse();
        expect(processSpy).toHaveBeenCalled();
        expect(sortSpy).toHaveBeenCalled();
        expect(mapSpy).toHaveBeenCalled();
    }));

    it('should fetch lectures for multi-launch when lectureIDs are provided', fakeAsync(() => {
        const lecture1 = new Lecture();
        lecture1.id = 1;
        const lecture2 = new Lecture();
        lecture2.id = 2;

        jest.spyOn(lectureService, 'find').mockImplementation((id: number) => of(new HttpResponse({ body: id === 1 ? lecture1 : lecture2 })));

        (TestBed.inject(ActivatedRoute) as any).queryParams = of({ lectureIDs: '1,2' });

        component.ngOnInit();

        expect(component.multiLaunchLectureIDs).toEqual([1, 2]);
        expect(lectureService.find).toHaveBeenCalledTimes(4);
        expect(lectureService.find).toHaveBeenCalledWith(1);
        expect(lectureService.find).toHaveBeenCalledWith(2);
    }));
});

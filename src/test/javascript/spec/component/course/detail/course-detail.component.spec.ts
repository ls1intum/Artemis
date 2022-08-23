import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';
import { CourseDetailComponent } from 'app/course/manage/detail/course-detail.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import dayjs from 'dayjs/esm';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { CourseDetailDoughnutChartComponent } from 'app/course/manage/detail/course-detail-doughnut-chart.component';
import { CourseDetailLineChartComponent } from 'app/course/manage/detail/course-detail-line-chart.component';
import { CourseManagementDetailViewDto } from 'app/course/manage/course-management-detail-view-dto.model';
import { UsersImportButtonComponent } from 'app/shared/import/users-import-button.component';
import { EventManager } from 'app/core/util/event-manager.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { FullscreenComponent } from 'app/shared/fullscreen/fullscreen.component';
import { Course } from 'app/entities/course.model';

describe('Course Management Detail Component', () => {
    let component: CourseDetailComponent;
    let fixture: ComponentFixture<CourseDetailComponent>;
    let courseService: CourseManagementService;
    let eventManager: EventManager;

    const route = { params: of({ courseId: 1 }) };
    const course: Course = {
        id: 123,
        title: 'Course Title',
        description: 'Cras mattis iudicium purus sit amet fermentum. Gallia est omnis divisa in partes tres, quarum.',
        isAtLeastInstructor: true,
        endDate: dayjs().subtract(5, 'minutes'),
        courseArchivePath: 'some-path',
    };
    const dtoMock: CourseManagementDetailViewDto = {
        numberOfStudentsInCourse: 100,
        numberOfTeachingAssistantsInCourse: 5,
        numberOfEditorsInCourse: 5,
        numberOfInstructorsInCourse: 10,
        // assessments
        currentPercentageAssessments: 50,
        currentAbsoluteAssessments: 10,
        currentMaxAssessments: 20,
        // complaints
        currentPercentageComplaints: 60,
        currentAbsoluteComplaints: 6,
        currentMaxComplaints: 10,
        // feedback Request
        currentPercentageMoreFeedbacks: 70,
        currentAbsoluteMoreFeedbacks: 14,
        currentMaxMoreFeedbacks: 20,
        // average score
        currentPercentageAverageScore: 90,
        currentAbsoluteAverageScore: 90,
        currentMaxAverageScore: 100,
        activeStudents: [4, 10, 14, 35],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseDetailComponent,
                MockComponent(SecuredImageComponent),
                MockComponent(UsersImportButtonComponent),
                MockRouterLinkDirective,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(DeleteButtonDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseExamArchiveButtonComponent),
                MockDirective(HasAnyAuthorityDirective),
                MockComponent(CourseDetailDoughnutChartComponent),
                MockComponent(CourseDetailLineChartComponent),
                MockComponent(FullscreenComponent),
            ],
            providers: [{ provide: ActivatedRoute, useValue: route }, { provide: Router, useClass: MockRouter }, MockProvider(CourseManagementService)],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseDetailComponent);
        component = fixture.componentInstance;
        courseService = fixture.debugElement.injector.get(CourseManagementService);
        eventManager = fixture.debugElement.injector.get(EventManager);
    });

    beforeEach(fakeAsync(() => {
        const statsStub = jest.spyOn(courseService, 'getCourseStatisticsForDetailView');
        statsStub.mockReturnValue(of(new HttpResponse({ body: dtoMock })));
        const infoStub = jest.spyOn(courseService, 'find');
        infoStub.mockReturnValue(of(new HttpResponse({ body: course })));

        component.ngOnInit();
        tick();
    }));

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call registerChangeInCourses on init', () => {
        const registerSpy = jest.spyOn(component, 'registerChangeInCourses');

        fixture.detectChanges();
        component.ngOnInit();
        expect(component.courseDTO).toEqual(dtoMock);
        expect(component.course).toEqual(course);
        expect(registerSpy).toHaveBeenCalledTimes(2);
    });

    it('should destroy event subscriber onDestroy', () => {
        const destroySpy = jest.spyOn(eventManager, 'destroy');
        component.ngOnDestroy();
        expect(destroySpy).toHaveBeenCalledOnce();
    });

    it('should broadcast course modification on delete', () => {
        const broadcastSpy = jest.spyOn(eventManager, 'broadcast');
        const deleteStub = jest.spyOn(courseService, 'delete');
        deleteStub.mockReturnValue(of(new HttpResponse<void>()));

        const courseId = 444;
        component.deleteCourse(courseId);

        expect(deleteStub).toHaveBeenCalledWith(courseId);
        expect(broadcastSpy).toHaveBeenCalledWith({
            name: 'courseListModification',
            content: 'Deleted an course',
        });
    });
});

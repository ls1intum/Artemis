import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';
import { CourseDetailComponent } from 'app/course/manage/detail/course-detail.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import dayjs from 'dayjs';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { MockRouterLinkDirective } from '../../lecture-unit/lecture-unit-management.component.spec';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { CourseDetailDoughnutChartComponent } from 'app/course/manage/detail/course-detail-doughnut-chart.component';
import { CourseDetailLineChartComponent } from 'app/course/manage/detail/course-detail-line-chart.component';
import { CourseManagementDetailViewDto } from 'app/course/manage/course-management-detail-view-dto.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';

chai.use(sinonChai);
const expect = chai.expect;

describe('Course Management Detail Component', () => {
    let component: CourseDetailComponent;
    let fixture: ComponentFixture<CourseDetailComponent>;
    let courseService: CourseManagementService;
    let eventManager: EventManager;

    const route = { params: of({ courseId: 1 }) };
    const course = { id: 123, title: 'Course Title', isAtLeastInstructor: true, endDate: dayjs().subtract(5, 'minutes'), courseArchivePath: 'some-path' };
    const dtoMock = {
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
    } as CourseManagementDetailViewDto;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseDetailComponent,
                MockComponent(SecuredImageComponent),
                MockRouterLinkDirective,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(DeleteButtonDirective),
                MockComponent(AlertErrorComponent),
                MockDirective(AlertComponent),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseExamArchiveButtonComponent),
                MockDirective(HasAnyAuthorityDirective),
                MockComponent(CourseDetailDoughnutChartComponent),
                MockComponent(CourseDetailLineChartComponent),
            ],
            providers: [{ provide: ActivatedRoute, useValue: route }, { provide: Router, useClass: MockRouter }, MockProvider(CourseManagementService)],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseDetailComponent);
        component = fixture.componentInstance;
        courseService = fixture.debugElement.injector.get(CourseManagementService);
        eventManager = fixture.debugElement.injector.get(EventManager);
    });

    beforeEach(fakeAsync(() => {
        const statsStub = sinon.stub(courseService, 'getCourseStatisticsForDetailView');
        statsStub.returns(of(new HttpResponse({ body: dtoMock })));
        const infoStub = sinon.stub(courseService, 'find');
        infoStub.returns(of(new HttpResponse({ body: course })));

        component.ngOnInit();
        tick();
    }));

    afterEach(() => {
        sinon.restore();
    });

    it('Should call registerChangeInCourses on init', () => {
        const registerSpy = sinon.spy(component, 'registerChangeInCourses');

        fixture.detectChanges();
        component.ngOnInit();
        expect(component.courseDTO).to.deep.equal(dtoMock);
        expect(component.course).to.deep.equal(course);
        expect(registerSpy).to.have.been.called;
    });

    it('should destroy event subscriber onDestroy', () => {
        component.ngOnDestroy();
        expect(eventManager.destroy).to.have.been.called;
    });

    it('should broadcast course modification on delete', () => {
        const deleteStub = sinon.stub(courseService, 'delete');
        deleteStub.returns(of(new HttpResponse<void>()));

        const courseId = 444;
        component.deleteCourse(courseId);

        expect(deleteStub).to.have.been.calledWith(courseId);
        expect(eventManager.broadcast).to.have.been.calledWith({
            name: 'courseListModification',
            content: 'Deleted an course',
        });
    });
});

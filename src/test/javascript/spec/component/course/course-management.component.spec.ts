import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import * as moment from 'moment';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementComponent } from 'app/course/manage/course-management.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs/internal/observable/of';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { RouterTestingModule } from '@angular/router/testing';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { JhiSortByDirective, JhiSortDirective } from 'ng-jhipster';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { MomentModule } from 'ngx-moment';
import { CourseManagementCardComponent } from 'app/course/manage/overview/course-management-card.component';
import { CourseManagementOverviewDto } from 'app/course/manage/overview/course-management-overview-dto.model';
import { CourseManagementOverviewExerciseDetailsDTO } from 'app/course/manage/overview/course-management-overview-exercise-details-dto.model';
import { CourseManagementOverviewDetailsDto } from 'app/course/manage/overview/course-management-overview-details-dto.model';
import { CourseManagementOverviewStatisticsDto } from 'app/course/manage/overview/course-management-overview-statistics-dto.model';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/course/manage/overview/course-management-overview-exercise-statistics-dto.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseManagementComponent', () => {
    let fixture: ComponentFixture<CourseManagementComponent>;
    let component: CourseManagementComponent;
    let service: CourseManagementService;
    let guidedTourService: GuidedTourService;

    const pastExercise = {
        dueDate: moment().subtract(6, 'days'),
        assessmentDueDate: moment().subtract(1, 'days'),
    } as CourseManagementOverviewExerciseDetailsDTO;

    const currentExercise = {
        dueDate: moment().add(2, 'days'),
        releaseDate: moment().subtract(2, 'days'),
    } as CourseManagementOverviewExerciseDetailsDTO;

    const futureExercise1 = {
        releaseDate: moment().add(4, 'days'),
    } as CourseManagementOverviewExerciseDetailsDTO;

    const futureExercise2 = {
        releaseDate: moment().add(6, 'days'),
    } as CourseManagementOverviewExerciseDetailsDTO;

    const courseDTO187 = {
        courseId: 187,
        exerciseDetails: [pastExercise, currentExercise, futureExercise2, futureExercise1],
    } as CourseManagementOverviewDto;

    const courseDTO188 = {
        courseId: 188,
        exerciseDetails: [],
    } as CourseManagementOverviewDto;

    const course187 = {
        id: 187,
        testCourse: false,
        semester: 'SS19',
    } as Course;

    const course188 = {
        id: 188,
        testCourse: false,
        semester: 'WS19/20',
    } as Course;

    const courseDetailsDTO187 = new CourseManagementOverviewDetailsDto();
    courseDetailsDTO187.id = 187;
    courseDetailsDTO187.semester = 'SS19';

    const courseDetailsDTO188 = new CourseManagementOverviewDetailsDto();
    courseDetailsDTO187.id = 188;
    courseDetailsDTO187.semester = 'WS19/20';

    const courseStatisticsDTO = new CourseManagementOverviewStatisticsDto();
    const exerciseDTO = new CourseManagementOverviewExerciseStatisticsDTO();
    exerciseDTO.exerciseId = 1;
    exerciseDTO.exerciseMaxPoints = 10;
    exerciseDTO.averageScoreInPercent = 50;
    courseStatisticsDTO.exerciseDTOS = [exerciseDTO];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MomentModule],
            declarations: [
                CourseManagementComponent,
                MockDirective(OrionFilterDirective),
                MockComponent(AlertComponent),
                MockDirective(MockHasAnyAuthorityDirective),
                MockDirective(JhiSortByDirective),
                MockPipe(TranslatePipe),
                MockDirective(JhiSortDirective),
                MockPipe(ArtemisDatePipe),
                MockDirective(DeleteButtonDirective),
                MockComponent(CourseManagementCardComponent),
            ],
            providers: [{ provide: LocalStorageService, useClass: MockSyncStorage }, { provide: SessionStorageService, useClass: MockSyncStorage }, MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(CourseManagementService);
                guidedTourService = TestBed.inject(GuidedTourService);
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', () => {
        sinon.stub(service, 'getCourseOverview').returns(of(new HttpResponse({ body: [courseDetailsDTO187, courseDetailsDTO188] })));
        sinon.stub(service, 'getExercisesForManagementOverview').returns(of(new HttpResponse({ body: [courseDTO187, courseDTO188] })));
        sinon.stub(service, 'getStatsForManagementOverview').returns(of(new HttpResponse({ body: [] })));
        sinon.stub(service, 'getWithUserStats').returns(of(new HttpResponse({ body: [course187, course188] })));
        sinon.stub(guidedTourService, 'enableTourForCourseOverview').returns(course187);

        fixture.detectChanges();
        expect(component).to.be.ok;
        expect(component.showOnlyActive).to.be.true;
        component.toggleShowOnlyActive();
        expect(component.showOnlyActive).to.be.false;
        fixture.detectChanges();
        expect(component).to.be.ok;
    });
});

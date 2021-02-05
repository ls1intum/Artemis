import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
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

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseManagementComponent', () => {
    let fixture: ComponentFixture<CourseManagementComponent>;
    let component: CourseManagementComponent;
    let service: CourseManagementService;
    let guidedTourService: GuidedTourService;

    beforeEach(async () => {
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

    afterEach(async () => {
        jest.clearAllMocks();
    });

    it('should initialize', async () => {
        const courseSS = new Course();
        courseSS.id = 187;
        courseSS.testCourse = false;
        courseSS.semester = 'SS19';
        const courseWS = new Course();
        courseWS.id = 188;
        courseWS.testCourse = false;
        courseWS.semester = 'WS19/20';

        const resp = [courseSS, courseWS];
        spyOn(service, 'getWithUserStats').and.returnValue(of(new HttpResponse({ body: resp })));
        spyOn(guidedTourService, 'enableTourForCourseOverview').and.returnValue(courseSS);

        fixture.detectChanges();
        expect(component).to.be.ok;
        expect(component.showOnlyActive).to.be.true;
        component.toggleShowOnlyActive();
        expect(component.showOnlyActive).to.be.false;
        fixture.detectChanges();
        expect(component).to.be.ok;
    });

    it('should delete course', async () => {
        const course = new Course();
        course.id = 187;
        spyOn(service, 'delete').and.returnValue(of(new HttpResponse({ body: null })));

        component.deleteCourse(187);
        expect(component).to.be.ok;
    });
});

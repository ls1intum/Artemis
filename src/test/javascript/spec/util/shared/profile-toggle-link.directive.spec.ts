import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileToggle, ProfileToggleService } from 'app/shared/profile-toggle/profile-toggle.service';
import { ArtemisTestModule } from '../../test.module';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../helpers/mocks/service/mock-websocket.service';
import { By } from '@angular/platform-browser';
import { Course } from 'app/entities/course.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ProfileToggleLinkDirective } from 'app/shared/profile-toggle/profile-toggle-link.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseManagementCardComponent } from 'app/course/manage/overview/course-management-card.component';
import { CourseManagementExerciseRowComponent } from 'app/course/manage/overview/course-management-exercise-row.component';
import { CourseManagementOverviewStatisticsComponent } from 'app/course/manage/overview/course-management-overview-statistics.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

describe('ProfileToggleLinkDirective', () => {
    let fixture: ComponentFixture<CourseManagementCardComponent>;
    let component: CourseManagementCardComponent;

    let profileToggleService: ProfileToggleService;

    const course = new Course();
    course.id = 1;
    course.color = 'red';
    course.isAtLeastEditor = true;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbModule],
            declarations: [
                ProfileToggleLinkDirective,
                CourseManagementCardComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockRouterLinkDirective,
                MockComponent(CourseManagementExerciseRowComponent),
                MockComponent(CourseManagementOverviewStatisticsComponent),
                MockComponent(SecuredImageComponent),
            ],
            providers: [
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                MockProvider(TranslateService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementCardComponent);
                component = fixture.componentInstance;
                component.course = course;

                profileToggleService = TestBed.inject(ProfileToggleService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should always be enabled if decoupling is not enabled', () => {
        profileToggleService.initializeProfileToggles([]);

        fixture.detectChanges();
        component.ngOnChanges();

        let lectureButton = fixture.debugElement.query(By.directive(ProfileToggleLinkDirective));
        // Not hidden because 'DECOUPLING' is not activated
        expect(lectureButton.classes['disabled']).toBeFalsy();

        profileToggleService.initializeProfileToggles([ProfileToggle.LECTURE]);

        fixture.detectChanges();
        component.ngOnChanges();

        lectureButton = fixture.debugElement.query(By.directive(ProfileToggleLinkDirective));
        // Not hidden because 'DECOUPLING' is not activated - independent of LECTURE
        expect(lectureButton.classes['disabled']).toBeFalsy();
    });

    it('should always be disabled if decoupling is enabled', () => {
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING]);

        fixture.detectChanges();
        component.ngOnChanges();

        const lectureButton = fixture.debugElement.query(By.directive(ProfileToggleLinkDirective));
        // Hidden because DECOUPLING is present but LECTURE is not
        expect(lectureButton.classes['disabled']).toBeTrue();
    });

    it('should toggle correctly if decoupling is enabled', () => {
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING]);

        fixture.detectChanges();
        component.ngOnChanges();

        let lectureButton = fixture.debugElement.query(By.directive(ProfileToggleLinkDirective));
        // Hidden because DECOUPLING is present but LECTURE is not
        expect(lectureButton.classes['disabled']).toBeTrue();

        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING, ProfileToggle.LECTURE]);

        fixture.detectChanges();
        component.ngOnChanges();

        lectureButton = fixture.debugElement.query(By.directive(ProfileToggleLinkDirective));
        // Not hidden because LECTURE is present
        expect(lectureButton.classes['disabled']).toBeFalsy();
    });
});

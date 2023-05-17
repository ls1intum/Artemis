import { RouterTestingModule } from '@angular/router/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileToggle, ProfileToggleService } from 'app/shared/profile-toggle/profile-toggle.service';
import { ArtemisTestModule } from '../../test.module';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../helpers/mocks/service/mock-websocket.service';
import { By } from '@angular/platform-browser';
import { ProfileToggleHideDirective } from 'app/shared/profile-toggle/profile-toggle-hide.directive';
import { Course } from 'app/entities/course.model';
import { CourseCardComponent } from 'app/overview/course-card.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { PieChartModule } from '@swimlane/ngx-charts';

describe('ProfileToggleHideDirective', () => {
    let fixture: ComponentFixture<CourseCardComponent>;
    let component: CourseCardComponent;

    let profileToggleService: ProfileToggleService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MockModule(PieChartModule)],
            declarations: [
                CourseCardComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockRouterLinkDirective,
                MockComponent(SecuredImageComponent),
                MockDirective(TranslateDirective),
                ProfileToggleHideDirective,
            ],
            providers: [{ provide: JhiWebsocketService, useClass: MockWebsocketService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseCardComponent);
                component = fixture.componentInstance;
                profileToggleService = TestBed.inject(ProfileToggleService);

                const course = new Course();
                course.id = 123;
                component.course = course;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should always be enabled if decoupling is not enabled', () => {
        profileToggleService.initializeProfileToggles([]);

        fixture.detectChanges();
        component.ngOnChanges();

        let lectureButton = fixture.debugElement.query(By.directive(ProfileToggleHideDirective));
        // Not hidden because 'DECOUPLING' is not activated
        expect(lectureButton.classes['d-none']).toBeFalsy();

        profileToggleService.initializeProfileToggles([ProfileToggle.LECTURE]);

        fixture.detectChanges();
        component.ngOnChanges();

        lectureButton = fixture.debugElement.query(By.directive(ProfileToggleHideDirective));
        // Not hidden because 'DECOUPLING' is not activated - independent of LECTURE
        expect(lectureButton.classes['d-none']).toBeFalsy();
    });

    it('should always be disabled if decoupling is enabled', () => {
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING]);

        fixture.detectChanges();
        component.ngOnChanges();

        const lectureButton = fixture.debugElement.query(By.directive(ProfileToggleHideDirective));
        // Hidden because DECOUPLING is present but LECTURE is not
        expect(lectureButton.classes['d-none']).toBeTrue();
    });

    it('should toggle correctly if decoupling is enabled', () => {
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING]);

        fixture.detectChanges();
        component.ngOnChanges();

        let lectureButton = fixture.debugElement.query(By.directive(ProfileToggleHideDirective));
        console.log(lectureButton.classes['d-none']);
        // Hidden because DECOUPLING is present but LECTURE is not
        expect(lectureButton.classes['d-none']).toBeTrue();

        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING, ProfileToggle.LECTURE]);

        fixture.detectChanges();
        component.ngOnChanges();

        lectureButton = fixture.debugElement.query(By.directive(ProfileToggleHideDirective));
        // Not hidden because LECTURE is present
        expect(lectureButton.classes['d-none']).toBeFalsy();
    });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';
import { CookieService } from 'ngx-cookie-service';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisTestModule } from '../../test.module';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';
import { DeviceDetectorService } from 'ngx-device-detector';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockCookieService } from '../../helpers/mocks/service/mock-cookie.service';
import { RouterTestingModule } from '@angular/router/testing';
import { courseOverviewTour } from 'app/guided-tour/tours/course-overview-tour';
import { CoursesComponent } from 'app/overview/courses.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { NavbarComponent } from 'app/shared/layouts/navbar/navbar.component';
import { ActiveMenuDirective } from 'app/shared/layouts/navbar/active-menu.directive';
import { NotificationSidebarComponent } from 'app/shared/notification/notification-sidebar/notification-sidebar.component';
import { User } from 'app/core/user/user.model';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { CourseCardComponent } from 'app/overview/course-card.component';
import { Course } from 'app/entities/course.model';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { FooterComponent } from 'app/shared/layouts/footer/footer.component';
import { LoadingNotificationComponent } from 'app/shared/notification/loading-notification/loading-notification.component';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../helpers/mocks/service/mock-metis-service.service';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { CourseRegistrationComponent } from 'app/overview/course-registration/course-registration.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FindLanguageFromKeyPipe } from 'app/shared/language/find-language-from-key.pipe';
import { NgbCollapse, NgbDropdown, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { PieChartModule } from '@swimlane/ngx-charts';

describe('Guided tour integration', () => {
    const user = { id: 1 } as User;
    const course = { id: 1, color: ARTEMIS_DEFAULT_COLOR } as Course;
    let guidedTourComponent: GuidedTourComponent;
    let guidedTourComponentFixture: ComponentFixture<GuidedTourComponent>;
    let navBarComponent: NavbarComponent;
    let navBarComponentFixture: ComponentFixture<NavbarComponent>;
    let courseCardComponent: CourseCardComponent;
    let courseCardComponentFixture: ComponentFixture<CourseCardComponent>;
    let footerComponentFixture: ComponentFixture<FooterComponent>;
    let guidedTourService: GuidedTourService;
    let exerciseService: ExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                RouterTestingModule.withRoutes([
                    {
                        path: 'courses',
                        component: MockComponent(CoursesComponent),
                    },
                ]),
                MockModule(PieChartModule),
            ],
            declarations: [
                CourseCardComponent,
                GuidedTourComponent,
                NavbarComponent,
                FooterComponent,
                NotificationSidebarComponent,
                MockHasAnyAuthorityDirective,
                MockComponent(FaIconComponent),
                MockComponent(CourseRegistrationComponent),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(LoadingNotificationComponent),
                MockComponent(CoursesComponent),
                MockComponent(SecuredImageComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(SafeResourceUrlPipe),
                MockPipe(FindLanguageFromKeyPipe),
                MockDirective(ActiveMenuDirective),
                MockDirective(TranslateDirective),
                MockDirective(NgbTooltip),
                MockDirective(NgbCollapse),
                MockDirective(NgbDropdown),
            ],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: CookieService, useClass: MockCookieService },
                { provide: DeviceDetectorService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: MetisService, useClass: MockMetisService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                guidedTourComponentFixture = TestBed.createComponent(GuidedTourComponent);
                courseCardComponentFixture = TestBed.createComponent(CourseCardComponent);
                navBarComponentFixture = TestBed.createComponent(NavbarComponent);
                footerComponentFixture = TestBed.createComponent(FooterComponent);

                guidedTourComponent = guidedTourComponentFixture.componentInstance;
                navBarComponent = navBarComponentFixture.componentInstance;
                courseCardComponent = courseCardComponentFixture.componentInstance;

                guidedTourService = TestBed.inject(GuidedTourService);
                exerciseService = TestBed.inject(ExerciseService);

                jest.spyOn(navBarComponentFixture.componentInstance, 'ngOnInit').mockImplementation(() => {
                    navBarComponent.currAccount = user;
                });

                jest.spyOn<any, any>(guidedTourComponent, 'subscribeToDotChanges').mockReturnValue(of());
                jest.spyOn(exerciseService, 'getNextExercisesForDays').mockReturnValue([]);
                jest.spyOn(guidedTourService, 'init').mockImplementation();
                jest.spyOn<any, any>(guidedTourService, 'updateGuidedTourSettings').mockReturnValue(of());
                jest.spyOn<any, any>(guidedTourService, 'checkTourState').mockReturnValue(true);
                jest.spyOn<any, any>(guidedTourService, 'checkSelectorValidity').mockReturnValue(true);
                jest.spyOn<any, any>(guidedTourService, 'enableTour').mockImplementation(() => {
                    guidedTourService['availableTourForComponent'] = courseOverviewTour;
                    guidedTourService.currentTour = courseOverviewTour;
                });
            });
    });

    function startGuidedTour() {
        guidedTourComponentFixture.componentInstance.ngAfterViewInit();

        // Start course overview tour
        guidedTourService['enableTour'](courseOverviewTour, true);
        guidedTourService['startTour']();

        guidedTourComponentFixture.detectChanges();
        expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).not.toBe(null);
    }

    describe('Course Overview Tour', () => {
        beforeEach(() => {
            startGuidedTour();

            courseCardComponent.course = course;
            courseCardComponent.hasGuidedTour = true;
        });

        it('should start the course overview guided tour', () => {
            window.scrollTo = () => {};

            const guidedTourSteps = guidedTourService['getFilteredTourSteps']().length;
            guidedTourComponentFixture.autoDetectChanges(true);
            courseCardComponentFixture.autoDetectChanges(true);
            navBarComponentFixture.autoDetectChanges(true);

            expect(guidedTourService.isOnFirstStep).toBe(true);
            expect(guidedTourSteps).toBe(9);

            // Click through tour steps in NavComponent
            for (let i = 1; i < 6; i++) {
                guidedTourService.nextStep();
                expect(guidedTourService.isOnFirstStep).toBe(false);
                guidedTourComponent.currentTourStep = guidedTourService['currentStep'];

                if (guidedTourComponent.currentTourStep.highlightSelector) {
                    const selectedElement = navBarComponentFixture.debugElement.query(By.css(guidedTourComponent.currentTourStep.highlightSelector));
                    expect(selectedElement).not.toBe(null);
                }
            }

            // Click through tour steps in CourseCardComponent
            for (let i = 6; i < 8; i++) {
                guidedTourService.nextStep();
                guidedTourComponent.currentTourStep = guidedTourService['currentStep'];

                if (guidedTourComponent.currentTourStep.highlightSelector) {
                    const selectedElement = courseCardComponentFixture.debugElement.query(By.css(guidedTourComponent.currentTourStep.highlightSelector));
                    expect(selectedElement).not.toBe(null);
                }
            }

            // Click through tour steps in FooterComponent
            for (let i = 8; i < guidedTourSteps; i++) {
                guidedTourService.nextStep();
                guidedTourComponent.currentTourStep = guidedTourService['currentStep'];

                if (guidedTourComponent.currentTourStep.highlightSelector) {
                    const selectedElement = footerComponentFixture.debugElement.query(By.css(guidedTourComponent.currentTourStep.highlightSelector));
                    expect(selectedElement).not.toBe(null);
                }
            }

            expect(guidedTourService.isOnLastStep).toBe(true);
        });
    });
});

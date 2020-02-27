import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed, tick, inject, fakeAsync } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { CookieService } from 'ngx-cookie-service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisTestModule } from '../../test.module';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { MockAccountService } from '../../mocks/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';
import { DeviceDetectorService } from 'ngx-device-detector';
import { MockSyncStorage } from '../../mocks/mock-sync.storage';
import { MockCookieService } from '../../mocks/mock-cookie.service';
import { RouterTestingModule } from '@angular/router/testing';
import { courseOverviewTour } from 'app/guided-tour/tours/course-overview-tour';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { CoursesComponent } from 'app/overview/courses.component';
import { ArtemisCoursesModule } from 'app/overview/courses.module';
import { TranslateTestingModule } from '../../mocks/mock-translate.service';
import { NavbarComponent } from 'app/shared/layouts/navbar/navbar.component';
import { ActiveMenuDirective } from 'app/shared/layouts/navbar/active-menu.directive';
import { NotificationContainerComponent } from 'app/shared/layouts/notification-container/notification-container.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('Courses Component', () => {
    describe('Guided Tour', () => {
        let guidedTourComponent: GuidedTourComponent;
        let guidedTourComponentFixture: ComponentFixture<GuidedTourComponent>;
        let navBarComponent: NavbarComponent;
        let navBarComponentFixture: ComponentFixture<NavbarComponent>;
        let courseComponent: CoursesComponent;
        let courseComponentFixture: ComponentFixture<CoursesComponent>;

        let guidedTourService: GuidedTourService;
        let courseExerciseService: CourseExerciseService;
        let router: Router;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [
                    ArtemisTestModule,
                    ArtemisSharedModule,
                    ArtemisCoursesModule,
                    TranslateTestingModule,
                    RouterTestingModule.withRoutes([
                        {
                            path: 'courses',
                            component: CoursesComponent,
                        },
                    ]),
                ],
                declarations: [ GuidedTourComponent, NavbarComponent, ActiveMenuDirective, NotificationContainerComponent ],
                providers: [
                    { provide: AccountService, useClass: MockAccountService },
                    { provide: CookieService, useClass: MockCookieService },
                    { provide: DeviceDetectorService },
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage }
                ],
            })
                .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
                .compileComponents()
                .then(() => {
                    guidedTourComponentFixture = TestBed.createComponent(GuidedTourComponent);
                    guidedTourComponent = guidedTourComponentFixture.componentInstance;

                    courseComponentFixture = TestBed.createComponent(CoursesComponent);
                    courseComponent = courseComponentFixture.componentInstance;

                    navBarComponentFixture = TestBed.createComponent(NavbarComponent);
                    navBarComponent = navBarComponentFixture.componentInstance;

                    router = TestBed.inject(Router);
                    guidedTourService = TestBed.inject(GuidedTourService);
                    courseExerciseService = TestBed.inject(CourseExerciseService);

                    spyOn(navBarComponent, 'ngOnInit').and.callFake(() => { });

                    spyOn(guidedTourService, 'init').and.returnValue(of());
                    spyOn<any>(guidedTourService, 'updateGuidedTourSettings').and.returnValue(of());

                    spyOn<any>(guidedTourService, 'checkTourState').and.returnValue(true);
                    spyOn<any>(guidedTourService, 'checkSelectorValidity').and.returnValue(true);

                    spyOn<any>(guidedTourService, 'enableTour').and.callFake(() => {
                        guidedTourService['availableTourForComponent'] = courseOverviewTour;
                        guidedTourService.currentTour = courseOverviewTour;
                    });

                });
        });

        async function startGuidedTour() {
            guidedTourComponent.ngAfterViewInit();

            await guidedTourComponentFixture.ngZone!.run(() => {
                router.navigateByUrl('/courses');
            });

            // Start course overview tour
            guidedTourService['enableTour'](courseOverviewTour, true);
            guidedTourService['startTour']();

            guidedTourComponentFixture.detectChanges();
            expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).to.exist;
        }

        describe('Course Overview Tour', () => {
            beforeEach(async () => {
                await startGuidedTour();
            });

            it('should start the course overview guided tour', () => {
                window.scrollTo = () => {};

                expect(guidedTourService.isOnFirstStep).to.be.true;
                expect(guidedTourService['getFilteredTourSteps']().length).to.equal(9);

                // Navigate to next step
                const hintText = guidedTourComponentFixture.debugElement.query(By.css('.step-hint.alert-success'));
                const nextButton = guidedTourComponentFixture.debugElement.query(By.css('.next-button'));
                nextButton.nativeElement.click();
                expect(guidedTourService.isOnFirstStep).to.be.false;

                guidedTourComponentFixture.detectChanges();
                courseComponentFixture.detectChanges();
                navBarComponentFixture.detectChanges();

                // Click through tour steps
                for (let i = 2; i < 9; i++) {
                    nextButton.nativeElement.click();
                    guidedTourComponentFixture.detectChanges();
                    courseComponentFixture.detectChanges();
                    navBarComponentFixture.detectChanges();
                }
                expect(guidedTourService.isOnLastStep).to.be.true;
            });
        });
    });
});

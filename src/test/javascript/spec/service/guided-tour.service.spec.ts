import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { of } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { CookieService } from 'ngx-cookie';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

import { ArtemisSharedModule } from 'app/shared';
import { ArtemisTestModule } from '../test.module';
import { NavbarComponent } from 'app/layouts';
import { SERVER_API_URL } from 'app/app.constants';
import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { GuidedTourState, Orientation } from 'app/guided-tour/guided-tour.constants';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { MockCookieService, MockSyncStorage } from '../mocks';
import { GuidedTourSetting } from 'app/guided-tour/guided-tour-setting.model';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { MockAccountService } from '../mocks/mock-account.service';
import { AccountService } from 'app/core';

chai.use(sinonChai);
const expect = chai.expect;

describe('Service Tests', () => {
    const courseOverviewTour: GuidedTour = {
        settingsKey: 'course_overview_tour',
        preventBackdropFromAdvancing: true,
        steps: [
            new TextTourStep({
                selector: '.random-selector',
                headlineTranslateKey: '',
                contentTranslateKey: '',
            }),
            new TextTourStep({
                selector: '.random-selector',
                headlineTranslateKey: '',
                contentTranslateKey: '',
                orientation: Orientation.TOPLEFT,
            }),
        ],
    };

    const courseOverviewTourWithUserInteraction: GuidedTour = {
        settingsKey: 'course_overview_tour',
        preventBackdropFromAdvancing: true,
        steps: [
            new TextTourStep({
                selector: '.random-selector',
                headlineTranslateKey: '',
                contentTranslateKey: '',
                enableUserInteraction: true,
            }),
            new TextTourStep({
                selector: '.random-selector',
                headlineTranslateKey: '',
                contentTranslateKey: '',
                orientation: Orientation.TOPLEFT,
            }),
        ],
    };

    describe('Guided Tour Service', () => {
        describe('Service methods', () => {
            let service: GuidedTourService;
            let httpMock: any;
            const expected = new GuidedTourSetting('guided_tour_key', 1, GuidedTourState.STARTED);

            beforeEach(() => {
                TestBed.configureTestingModule({
                    imports: [ArtemisTestModule, ArtemisSharedModule, HttpClientTestingModule],
                    providers: [GuidedTourService],
                });

                service = TestBed.get(GuidedTourService);
                httpMock = TestBed.get(HttpTestingController);
            });

            afterEach(() => {
                httpMock.verify();
            });

            it('should call correct update URL and return the right JSON object', () => {
                service.guidedTourSettings = [];
                service.updateGuidedTourSettings('guided_tour_key', 1, GuidedTourState.STARTED).subscribe();
                const req = httpMock.expectOne({ method: 'PUT' });
                const resourceUrl = SERVER_API_URL + 'api/guided-tour-settings';
                expect(req.request.url).equal(`${resourceUrl}`);
                expect(service.guidedTourSettings).to.eql([expected]);
            });
        });

        describe('Guided tour methods', () => {
            let guidedTourComponent: GuidedTourComponent;
            let guidedTourComponentFixture: ComponentFixture<GuidedTourComponent>;

            let navbarComponent: NavbarComponent;
            let navbarComponentFixture: ComponentFixture<NavbarComponent>;

            let guidedTourService: GuidedTourService;
            let router: Router;

            beforeEach(() => {
                TestBed.configureTestingModule({
                    imports: [
                        ArtemisTestModule,
                        RouterTestingModule.withRoutes([
                            {
                                path: 'overview',
                                component: NavbarComponent,
                            },
                        ]),
                    ],
                    schemas: [NO_ERRORS_SCHEMA],
                    declarations: [NavbarComponent, GuidedTourComponent],
                    providers: [
                        { provide: LocalStorageService, useClass: MockSyncStorage },
                        { provide: SessionStorageService, useClass: MockSyncStorage },
                        { provide: CookieService, useClass: MockCookieService },
                        { provide: AccountService, useClass: MockAccountService },
                    ],
                })
                    .overrideTemplate(NavbarComponent, '<div class="random-selector"></div>')
                    .compileComponents()
                    .then(() => {
                        guidedTourComponentFixture = TestBed.createComponent(GuidedTourComponent);
                        guidedTourComponent = guidedTourComponentFixture.componentInstance;

                        navbarComponentFixture = TestBed.createComponent(NavbarComponent);
                        navbarComponent = navbarComponentFixture.componentInstance;

                        guidedTourService = TestBed.get(GuidedTourService);
                        router = TestBed.get(Router);
                    });
            });

            async function prepareGuidedTour(tour: GuidedTour) {
                // Prepare GuidedTourService and GuidedTourComponent
                spyOn(guidedTourService, 'updateGuidedTourSettings').and.returnValue(of());
                spyOn(guidedTourService, 'init').and.returnValue(of());
                spyOn(guidedTourService, 'checkSelectorValidity').and.returnValue(true);
                spyOn(guidedTourService, 'enableTour').and.callFake(() => {
                    guidedTourService.currentTour = tour;
                });

                guidedTourComponent.ngAfterViewInit();

                await guidedTourComponentFixture.ngZone!.run(() => {
                    router.navigateByUrl('/overview');
                });

                // Start course overview tour
                expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).to.not.exist;
                guidedTourService.enableTour(tour);
                guidedTourService.startTour();
                guidedTourComponentFixture.detectChanges();
                expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).to.exist;
                expect(guidedTourService.isOnFirstStep).to.be.true;
                expect(guidedTourService.currentTourStepDisplay).to.equal(1);
                expect(guidedTourService.currentTourStepCount).to.equal(2);
            }

            describe('Start guided tours without user interaction', () => {
                beforeEach(async () => {
                    await prepareGuidedTour(courseOverviewTour);
                });

                it('should start and finish the course overview guided tour', async () => {
                    // Navigate to next step
                    const nextButton = guidedTourComponentFixture.debugElement.query(By.css('.next-button'));
                    expect(nextButton).to.exist;
                    nextButton.nativeElement.click();
                    expect(guidedTourService.isOnLastStep).to.be.true;

                    // Finish guided tour
                    nextButton.nativeElement.click();
                    guidedTourComponentFixture.detectChanges();
                    expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).to.not.exist;
                });

                it('should start and skip the tour', () => {
                    const skipButton = guidedTourComponentFixture.debugElement.query(By.css('.close'));
                    expect(skipButton).to.exist;
                    skipButton.nativeElement.click();
                    guidedTourComponentFixture.detectChanges();
                    expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).to.not.exist;
                });

                it('should prevent backdrop from advancing', () => {
                    const backdrop = guidedTourComponentFixture.debugElement.queryAll(By.css('.guided-tour-overlay'));
                    expect(backdrop).to.exist;
                    expect(backdrop.length).to.equal(4);
                    backdrop.forEach(overlay => {
                        overlay.nativeElement.click();
                    });
                    guidedTourComponentFixture.detectChanges();
                    expect(guidedTourService.isOnFirstStep).to.be.true;
                });
            });

            describe('Start guided tours with user interaction', () => {
                beforeEach(async () => {
                    await prepareGuidedTour(courseOverviewTourWithUserInteraction);
                });

                it('should enable user interaction and navigate to next step after user interaction', async () => {
                    // Fix jsom error: Not implemented: window.scrollTo
                    const jsdomAlert = window.scrollTo;
                    window.scrollTo = () => {};

                    const selector = navbarComponentFixture.debugElement.query(By.css('.random-selector'));

                    if (selector) {
                        selector.nativeElement.click();
                        await new Promise(resolve => setTimeout(() => resolve(), 500));
                        guidedTourComponentFixture.detectChanges();
                        expect(guidedTourService.isOnLastStep).to.be.true;
                    }
                    // Restore the jsdom alert
                    window.scrollTo = jsdomAlert;
                });
            });
        });
    });
});

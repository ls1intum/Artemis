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
import { ContentType, Orientation } from 'app/guided-tour/guided-tour.constants';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { MockCookieService, MockSyncStorage } from '../mocks';

chai.use(sinonChai);
const expect = chai.expect;

describe('Service Tests', () => {
    describe('Guided Tour Service', () => {
        describe('Service methods', () => {
            let service: GuidedTourService;
            let httpMock: any;

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

            it('should call correct URL', () => {
                const req = httpMock.expectOne({ method: 'GET' });
                const resourceUrl = SERVER_API_URL + 'api/guided-tour-settings';
                expect(req.request.url).equal(`${resourceUrl}`);
            });

            it('should return json', () => {
                const req = httpMock.expectOne({ method: 'GET' });
                expect(req.request.responseType).to.equal('json');
            });
        });

        describe('Guided tour methods', () => {
            let guidedTourComponent: GuidedTourComponent;
            let guidedTourComponentFixture: ComponentFixture<GuidedTourComponent>;

            let guidedTourService: GuidedTourService;
            let router: Router;

            const courseOverviewTour: GuidedTour = {
                settingsKey: 'course_overview_tour',
                preventBackdropFromAdvancing: true,
                steps: [
                    {
                        contentType: ContentType.IMAGE,
                        headlineTranslateKey: '',
                        subHeadlineTranslateKey: '',
                        contentTranslateKey: '',
                    },
                    {
                        contentType: ContentType.TEXT,
                        headlineTranslateKey: '',
                        contentTranslateKey: '',
                        orientation: Orientation.TOPLEFT,
                    },
                ],
            };

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
                    ],
                })
                    .overrideTemplate(NavbarComponent, '')
                    .compileComponents()
                    .then(() => {
                        guidedTourComponentFixture = TestBed.createComponent(GuidedTourComponent);
                        guidedTourComponent = guidedTourComponentFixture.componentInstance;

                        guidedTourService = TestBed.get(GuidedTourService);
                        router = TestBed.get(Router);
                    });
            });

            describe('Start tour method', () => {
                beforeEach(async () => {
                    // Prepare GuidedTourService and GuidedTourComponent
                    spyOn(guidedTourService, 'getOverviewTour').and.returnValue(of(courseOverviewTour));
                    spyOn(guidedTourService, 'updateGuidedTourSettings').and.returnValue(of());
                    guidedTourComponent.ngAfterViewInit();

                    await guidedTourComponentFixture.ngZone!.run(() => {
                        router.navigateByUrl('/overview');
                    });

                    // Start course overview tour
                    expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).to.not.exist;
                    expect(guidedTourService.checkGuidedTourAvailabilityForCurrentRoute()).to.be.true;
                    guidedTourService.startGuidedTourForCurrentRoute();
                    guidedTourComponentFixture.detectChanges();
                    expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).to.exist;
                    expect(guidedTourService.isOnFirstStep).to.be.true;
                    expect(guidedTourService.currentTourStepDisplay).to.equal(1);
                    expect(guidedTourService.currentTourStepCount).to.equal(2);
                });

                it('should start and finish the course overview guided tour', () => {
                    // Navigate to next tour step
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
                    const backdrop = guidedTourComponentFixture.debugElement.query(By.css('.guided-tour-user-input-mask'));
                    expect(backdrop).to.exist;
                    backdrop.nativeElement.click();
                    guidedTourComponentFixture.detectChanges();
                    expect(guidedTourService.isOnFirstStep).to.be.true;
                });
            });
        });
    });
});

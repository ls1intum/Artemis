import { NO_ERRORS_SCHEMA, DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { CookieService } from 'ngx-cookie';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';

import { ArTEMiSTestModule } from '../../test.module';
import { MockCookieService, MockSyncStorage } from '../../mocks';
import { NavbarComponent } from 'app/layouts';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { ContentType, GuidedTour, Orientation } from 'app/guided-tour/guided-tour.constants';

chai.use(sinonChai);
const expect = chai.expect;

describe('Component Tests', () => {
    const courseOverviewTour: GuidedTour = {
        settingsId: 'showCourseOverviewTour',
        preventBackdropFromAdvancing: true,
        steps: [
            {
                contentType: ContentType.IMAGE,
                headlineTranslateKey: 'tour.course-overview.welcome.headline',
                subHeadlineTranslateKey: 'tour.course-overview.welcome.subHeadline',
                contentTranslateKey: 'tour.course-overview.welcome.content',
            },
            {
                contentType: ContentType.TEXT,
                selector: '#overview-menu',
                headlineTranslateKey: 'tour.course-overview.overview-menu.headline',
                contentTranslateKey: 'tour.course-overview.overview-menu.content',
                orientation: Orientation.BottomLeft,
            },
        ],
    };

    describe('Guided Tour Component', () => {
        let guidedTourComponent: GuidedTourComponent;
        let guidedTourComponentFixture: ComponentFixture<GuidedTourComponent>;
        let guidedTourDebugElement: DebugElement;
        let guidedTourService: GuidedTourService;
        let router: Router;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [
                    ArTEMiSTestModule,
                    RouterTestingModule.withRoutes([
                        {
                            path: 'overview',
                            component: NavbarComponent,
                        },
                    ]),
                ],
                declarations: [GuidedTourComponent, NavbarComponent],
                schemas: [NO_ERRORS_SCHEMA],
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
                    guidedTourDebugElement = guidedTourComponentFixture.debugElement;
                    guidedTourService = TestBed.get(GuidedTourService);
                    router = TestBed.get(Router);
                });
        });

        describe('Invoke course overview guided tour', () => {
            beforeEach(async () => {
                // Prepare GuidedTourService and GuidedTourComponent
                spyOn(guidedTourService, 'getOverviewTour').and.returnValue(of(courseOverviewTour));
                spyOn(guidedTourService, 'updateGuidedTourSettings').and.returnValue(of());
                guidedTourComponent.ngAfterViewInit();

                await guidedTourComponentFixture.ngZone!.run(() => {
                    router.navigateByUrl('/overview');
                });

                // Start course overview tour
                expect(guidedTourComponent.currentTourStep).to.not.exist;
                guidedTourService.startGuidedTourForCurrentRoute();
                guidedTourComponentFixture.detectChanges();
                expect(guidedTourComponent.currentTourStep).to.exist;
            });

            it('should start the tour and navigate next with the right arrow key', () => {
                const nextStep = spyOn(guidedTourService, 'nextStep');
                const scrollEvent = spyOn(guidedTourComponent, 'scrollToAndSetElement');
                const eventMock = new KeyboardEvent('keydown', { code: 'ArrowRight' });
                guidedTourComponent.handleKeyboardEvent(eventMock);
                expect(nextStep.calls.count()).to.equal(1);
                nextStep.calls.reset();
            });

            it('should start the tour and navigate back with the left arrow key', () => {
                const backStep = spyOn(guidedTourService, 'backStep');
                const nextStep = spyOn(guidedTourService, 'nextStep');
                const eventMockRight = new KeyboardEvent('keydown', { code: 'ArrowRight' });
                const eventMockLeft = new KeyboardEvent('keydown', { code: 'ArrowLeft' });

                guidedTourComponent.handleKeyboardEvent(eventMockLeft);
                expect(backStep.calls.count()).to.equal(0);

                guidedTourComponent.handleKeyboardEvent(eventMockRight);
                expect(nextStep.calls.count()).to.equal(1);

                guidedTourComponent.handleKeyboardEvent(eventMockLeft);
                expect(nextStep.calls.count()).to.equal(1);

                nextStep.calls.reset();
                backStep.calls.reset();
            });

            it('should start the tour and skip it with the escape key', () => {
                const skipTour = spyOn(guidedTourService, 'skipTour');
                const eventMock = new KeyboardEvent('keydown', { code: 'Escape' });

                guidedTourComponent.handleKeyboardEvent(eventMock);
                expect(skipTour.calls.count()).to.equal(1);

                skipTour.calls.reset();
            });
        });
    });
});

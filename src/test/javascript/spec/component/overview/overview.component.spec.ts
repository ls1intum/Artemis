import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { CookieService } from 'ngx-cookie';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, async, fakeAsync, inject } from '@angular/core/testing';
import { OverviewComponent } from 'app/overview';
import { Course } from 'app/entities/course';
import { ArTEMiSTestModule } from '../../test.module';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { ContentType, GuidedTour, Orientation } from 'app/guided-tour/guided-tour.constants';
import { MockCookieService, MockSyncStorage } from '../../mocks';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('Component Tests', () => {
    const courseOverviewTour: GuidedTour = {
        settingsId: 'showCourseOverviewTour',
        steps: [
            {
                contentType: ContentType.IMAGE,
                headlineTranslateKey: 'tour.course-overview.welcome.headline',
                subHeadlineTranslateKey: 'tour.course-overview.welcome.subHeadline',
                contentTranslateKey: 'tour.course-overview.welcome.content',
            },
            {
                contentType: ContentType.TEXT,
                headlineTranslateKey: 'tour.course-overview.contact.headline',
                contentTranslateKey: 'tour.course-overview.contact.content',
                orientation: Orientation.TopLeft,
            },
        ],
    };

    describe('OverviewComponent', () => {
        let comp: OverviewComponent;
        let fixture: ComponentFixture<OverviewComponent>;
        let tourComp: GuidedTourComponent;
        let tourCompfixture: ComponentFixture<GuidedTourComponent>;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [OverviewComponent, GuidedTourComponent],
                schemas: [NO_ERRORS_SCHEMA],
                providers: [
                    GuidedTourService,
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: CookieService, useClass: MockCookieService },
                ],
            })
                .overrideTemplate(OverviewComponent, '')
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(OverviewComponent);
                    comp = fixture.componentInstance;
                    tourCompfixture = TestBed.createComponent(GuidedTourComponent);
                    tourComp = tourCompfixture.componentInstance;
                });
        }));

        it('starts and finishes the overview guided tour', inject(
            [GuidedTourService],
            fakeAsync((service: GuidedTourService) => {
                // Prepare GuidedTourService and GuidedTourComponent
                spyOn(service, 'getOverviewTour').and.returnValue(of(courseOverviewTour));
                tourComp.ngAfterViewInit();
                expect(tourCompfixture.debugElement.query(By.css('.tour-step'))).to.not.exist;
                service.getOverviewTour();

                // Start CourseOverviewTour from OverviewComponent
                comp.startTour();
                tourCompfixture.detectChanges();
                expect(tourCompfixture.debugElement.query(By.css('.tour-step'))).to.exist;
                expect(service.isOnFirstStep).to.be.true;

                // Navigate to next TourStep
                const nextButton = tourCompfixture.debugElement.query(By.css('.next-button'));
                expect(nextButton).to.exist;
                nextButton.nativeElement.click();
                expect(service.isOnLastStep).to.be.true;

                // Finish GuidedTour
                spyOn(service, 'updateGuidedTourSettings').and.returnValue(of());
                nextButton.nativeElement.click();
                tourCompfixture.detectChanges();
                expect(tourCompfixture.debugElement.query(By.css('.tour-step'))).to.not.exist;
            }),
        ));
    });
});

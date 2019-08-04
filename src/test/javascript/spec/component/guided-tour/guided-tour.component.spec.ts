import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, async, fakeAsync, inject } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';

import { ArTEMiSTestModule } from '../../test.module';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { ContentType, GuidedTour, Orientation } from 'app/guided-tour/guided-tour.constants';

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

    describe('GuidedTourComponent', () => {
        let comp: GuidedTourComponent;
        let fixture: ComponentFixture<GuidedTourComponent>;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [GuidedTourComponent],
                schemas: [NO_ERRORS_SCHEMA],
                providers: [GuidedTourService],
            })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(GuidedTourComponent);
                    comp = fixture.componentInstance;
                });
        }));

        it('starts the given guided tour', inject(
            [GuidedTourService],
            fakeAsync((service: GuidedTourService) => {
                expect(comp.currentTourStep).to.not.exist;

                comp.ngAfterViewInit();
                service.startTour(courseOverviewTour);

                expect(service.currentTourSteps.length).to.eq(2);
                expect(service.isOnFirstStep).to.be.true;
                expect(service.isOnLastStep).to.be.false;
                expect(comp.currentTourStep).to.exist;
            }),
        ));
    });
});

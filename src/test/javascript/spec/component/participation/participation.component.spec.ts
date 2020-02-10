import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ActivatedRoute } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared';
import { MockSyncStorage, MockActivatedRoute } from '../../mocks';
import { StudentParticipation } from 'app/entities/participation';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { ParticipationComponent } from 'app/entities/participation/participation.component';
import { Course } from 'app/entities/course';
import { Exercise } from 'app/entities/exercise';
import { of } from 'rxjs';

chai.use(sinonChai);
const expect = chai.expect;

describe('ParticipationComponent', () => {
    let component: ParticipationComponent;
    let componentFixture: ComponentFixture<ParticipationComponent>;
    let service: ParticipationService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule],
            declarations: [ParticipationComponent],
            providers: [
                { provide: ActivatedRoute, useClass: MockActivatedRoute },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .overrideTemplate(ParticipationComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(ParticipationComponent);
                component = componentFixture.componentInstance;
                service = TestBed.get(ParticipationService);
            });
    });

    describe('Presentation Score', () => {
        let updateSpy: jasmine.Spy;

        beforeEach(() => {
            updateSpy = spyOn(service, 'update').and.returnValue(of());
        });

        const courseWithPresentationScore = {
            id: 1,
            title: 'Presentation Score',
            presentationScore: 2,
        } as Course;

        const courseWithoutPresentationScore = {
            id: 2,
            title: 'No Presentation Score',
            presentationScore: 0,
        } as Course;

        const exercise1 = {
            id: 1,
            title: 'Exercise 1',
            course: courseWithPresentationScore,
            presentationScoreEnabled: true,
            isAtLeastTutor: true,
        } as Exercise;

        const exercise2 = {
            id: 2,
            title: 'Exercise 2',
            course: courseWithoutPresentationScore,
            presentationScoreEnabled: false,
            isAtLeastTutor: true,
        } as Exercise;

        const participation = {
            student: { id: 1 },
            exercise: exercise1,
        } as StudentParticipation;

        it('should add a presentation score if the feature is enabled', () => {
            component.exercise = exercise1;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();
            component.addPresentation(participation);
            expect(updateSpy.calls.count()).to.equal(1);
            updateSpy.calls.reset();

            component.exercise = exercise2;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();
            component.addPresentation(participation);
            expect(updateSpy.calls.count()).to.equal(0);
        });

        it('should remove a presentation score if the feature is enabled', () => {
            component.exercise = exercise1;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();
            component.removePresentation(participation);
            expect(updateSpy.calls.count()).to.equal(1);
            updateSpy.calls.reset();

            component.exercise = exercise2;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();
            component.removePresentation(participation);
            expect(updateSpy.calls.count()).to.equal(0);
        });

        it('should check if the presentation score actions should be displayed', () => {
            component.exercise = exercise1;
            expect(component.checkPresentationScoreConfig()).to.be.true;

            component.exercise = exercise2;
            expect(component.checkPresentationScoreConfig()).to.be.false;
        });
    });
});

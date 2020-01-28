import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared';
import { MockSyncStorage } from '../../mocks';
import { TranslateModule } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TutorParticipationGraphComponent } from 'app/tutor-course-dashboard/tutor-participation-graph/tutor-participation-graph.component';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/tutor-participation';
import { Exercise } from 'app/entities/exercise';
import { ProgressBarComponent } from 'app/tutor-course-dashboard/tutor-participation-graph/progress-bar/progress-bar.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('TutorParticipationGraphComponent', () => {
    let comp: TutorParticipationGraphComponent;
    let fixture: ComponentFixture<TutorParticipationGraphComponent>;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, TranslateModule.forRoot()],
            declarations: [TutorParticipationGraphComponent, ProgressBarComponent],
            providers: [JhiLanguageHelper, { provide: LocalStorageService, useClass: MockSyncStorage }, { provide: SessionStorageService, useClass: MockSyncStorage }],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorParticipationGraphComponent);
                comp = fixture.componentInstance;
            });
    });

    describe('Participation Status Method', () => {
        beforeEach(() => {
            comp.exercise = {
                id: 1,
                exampleSubmissions: [{ id: 1, usedForTutorial: true }],
            } as Exercise;

            comp.tutorParticipation = {
                id: 1,
                trainedExampleSubmissions: [{ id: 1, usedForTutorial: true }],
            } as TutorParticipation;
        });

        it('should calculate the right classes for the initial NOT_PARTICIPATED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.NOT_PARTICIPATED;

            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).to.equal('active');
            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).to.equal('opaque');
        });

        it('should calculate the right classes for the REVIEWED_INSTRUCTIONS status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.REVIEWED_INSTRUCTIONS;

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).to.equal('active');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).to.equal('');
        });

        it('should calculate the right classes for the TRAINED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.TRAINED;

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).to.equal('');

            comp.exercise = {
                id: 1,
                exampleSubmissions: [
                    { id: 1, usedForTutorial: false },
                    { id: 2, usedForTutorial: true },
                ],
            } as Exercise;

            comp.tutorParticipation = {
                id: 1,
                trainedExampleSubmissions: [{ id: 1, usedForTutorial: false }],
            } as TutorParticipation;

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).to.equal('orange');
        });

        it('should calculate the right classes for the COMPLETED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.COMPLETED;

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).to.equal('');
        });
    });
});

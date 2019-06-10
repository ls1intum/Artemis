import { SimpleChange, SimpleChanges } from '@angular/core';
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { CookieService } from 'ngx-cookie';
import { AceEditorModule } from 'ng2-ace-editor';
import * as chai from 'chai';
import { Subject } from 'rxjs';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/entities/programming-exercise';
import { ParticipationType } from 'app/entities/programming-exercise';
import { ArTEMiSTestModule } from '../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { MockSyncStorage } from '../../mocks';
import { MockCookieService } from '../../mocks/mock-cookie.service';
import { SinonStub, stub } from 'sinon';
import { Result } from '../../../../../main/webapp/app/entities/result';
import { ParticipationWebsocketService } from '../../../../../main/webapp/app/entities/participation';

const expect = chai.expect;

describe('ProgrammingExerciseInstructorStatusComponent', () => {
    let comp: ProgrammingExerciseInstructorStatusComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorStatusComponent>;
    let participationWebsocketService: ParticipationWebsocketService;
    let subscribeForLatestResultStub: SinonStub;
    let addParticipationToListStub: SinonStub;
    let latestResultSubject: Subject<Result>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, AceEditorModule, NgbModule],
            declarations: [ProgrammingExerciseInstructorStatusComponent],
            providers: [
                { provide: CookieService, useClass: MockCookieService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructorStatusComponent);
                comp = fixture.componentInstance as ProgrammingExerciseInstructorStatusComponent;

                participationWebsocketService = fixture.debugElement.injector.get(ParticipationWebsocketService);

                addParticipationToListStub = stub(participationWebsocketService, 'addParticipation');
                subscribeForLatestResultStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
                latestResultSubject = new Subject();
                subscribeForLatestResultStub.returns(latestResultSubject);
            });
    }));

    afterEach(() => {
        subscribeForLatestResultStub.restore();
        latestResultSubject.complete();
        latestResultSubject = new Subject();
        subscribeForLatestResultStub.returns(latestResultSubject);
    });

    it('should not show anything without inputs', () => {
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).to.not.exist;
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).to.not.exist;
    });

    it('should not show anything if participationType is Assignment', () => {
        comp.participationType = ParticipationType.ASSIGNMENT;
        comp.participation = { id: 1, results: [{ id: 1, successful: true, score: 100 }] };
        fixture.detectChanges();
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).to.not.exist;
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).to.not.exist;
    });

    [ParticipationType.TEMPLATE, ParticipationType.SOLUTION].map(participationType =>
        it('should not show anything if there is no participation', () => {
            comp.participationType = participationType;
            comp.participation = null;
            fixture.detectChanges();
            const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
            expect(templateStatus).to.not.exist;
            const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
            expect(solutionStatus).to.not.exist;
        }),
    );

    it('should show nothing if the participation is template and the latest result has a score of 0', () => {
        const latestResult = { id: 3, successful: false, score: 0 };
        comp.participationType = ParticipationType.TEMPLATE;
        comp.participation = { id: 1, results: [latestResult, { id: 2, successful: false, score: 99 }] };
        comp.exercise = { id: 99 };
        const changes: SimpleChanges = {
            participationType: new SimpleChange(undefined, comp.participationType, true),
            participation: new SimpleChange(undefined, comp.participation, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();
        expect(comp.latestResult).to.deep.equal(latestResult);
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).to.not.exist;
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).to.not.exist;
    });

    it('should show nothing if the participation is solution and the latest result is successful', () => {
        const latestResult = { id: 3, successful: true, score: 100 };
        comp.participationType = ParticipationType.SOLUTION;
        comp.participation = { id: 1, results: [{ id: 2, successful: false, score: 99 }, latestResult] };
        comp.exercise = { id: 99 };
        const changes: SimpleChanges = {
            participationType: new SimpleChange(undefined, comp.participationType, false),
            participation: new SimpleChange(undefined, comp.participation, false),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();
        expect(comp.latestResult).to.deep.equal(latestResult);
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).to.not.exist;
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).to.not.exist;
    });

    it('should show a template warning if the participation is template and the score is > 0', () => {
        const latestResult = { id: 3, successful: false, score: 40 };
        comp.participationType = ParticipationType.TEMPLATE;
        comp.participation = { id: 1, results: [latestResult, { id: 2, successful: false, score: 99 }] };
        comp.exercise = { id: 99 };
        const changes: SimpleChanges = {
            participationType: new SimpleChange(undefined, comp.participationType, false),
            participation: new SimpleChange(undefined, comp.participation, false),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();
        expect(comp.latestResult).to.deep.equal(latestResult);
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).to.exist;
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).to.not.exist;
    });

    it('should show a solution warning if the participation is solution and the result is not successful', () => {
        const latestResult = { id: 3, successful: false, score: 40 };
        comp.participationType = ParticipationType.SOLUTION;
        comp.participation = { id: 1, results: [{ id: 2, successful: false, score: 99 }, latestResult] };
        comp.exercise = { id: 99 };
        const changes: SimpleChanges = {
            participationType: new SimpleChange(undefined, comp.participationType, true),
            participation: new SimpleChange(undefined, comp.participation, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();
        expect(comp.latestResult).to.deep.equal(latestResult);
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).to.not.exist;
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).to.exist;
    });

    it('should update the latestResult on update from the result subscription', () => {
        const newResult = { id: 4, successful: true, score: 40 } as Result;
        const latestResult = { id: 3, successful: false, score: 40 };
        comp.participationType = ParticipationType.TEMPLATE;
        comp.participation = { id: 1, results: [latestResult, { id: 2, successful: false, score: 99 }] };
        comp.exercise = { id: 99 };
        const changes: SimpleChanges = {
            participationType: new SimpleChange(undefined, comp.participationType, false),
            participation: new SimpleChange(undefined, comp.participation, false),
        };
        comp.ngOnChanges(changes);
        latestResultSubject.next(newResult);

        expect(comp.latestResult).to.deep.equal(newResult);
    });
});

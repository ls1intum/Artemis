import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Subject } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { Result } from 'app/entities/result.model';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/exercises/programming/manage/status/programming-exercise-instructor-status.component';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming-exercise-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

describe('ProgrammingExerciseInstructorStatusComponent', () => {
    let comp: ProgrammingExerciseInstructorStatusComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorStatusComponent>;
    let participationWebsocketService: ParticipationWebsocketService;
    let subscribeForLatestResultStub: jest.SpyInstance;
    let latestResultSubject: Subject<Result>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockDirective(NgbTooltip)],
            declarations: [ProgrammingExerciseInstructorStatusComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructorStatusComponent);
                comp = fixture.componentInstance as ProgrammingExerciseInstructorStatusComponent;

                participationWebsocketService = fixture.debugElement.injector.get(ParticipationWebsocketService);

                subscribeForLatestResultStub = jest.spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
                latestResultSubject = new Subject();
                subscribeForLatestResultStub.mockReturnValue(latestResultSubject);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        latestResultSubject.complete();
        latestResultSubject = new Subject();
        subscribeForLatestResultStub.mockReturnValue(latestResultSubject);
    });

    it('should not show anything without inputs', () => {
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).toBeNull();
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).toBeNull();
    });

    it('should not show anything if participationType is Assignment', () => {
        comp.participationType = ProgrammingExerciseParticipationType.ASSIGNMENT;
        comp.participation = { id: 1, results: [{ id: 1, successful: true, score: 100 } as Result] } as ProgrammingExerciseStudentParticipation;
        fixture.detectChanges();
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).toBeNull();
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).toBeNull();
    });

    [ProgrammingExerciseParticipationType.TEMPLATE, ProgrammingExerciseParticipationType.SOLUTION].map((participationType) =>
        it('should not show anything if there is no participation', () => {
            comp.participationType = participationType;
            fixture.detectChanges();
            const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
            expect(templateStatus).toBeNull();
            const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
            expect(solutionStatus).toBeNull();
        }),
    );

    it('should show nothing if the participation is template and the latest result has a score of 0', () => {
        const latestResult = { id: 3, successful: false, score: 0 } as Result;
        comp.participationType = ProgrammingExerciseParticipationType.TEMPLATE;
        comp.participation = { id: 1, results: [latestResult, { id: 2, successful: false, score: 99 } as Result] } as TemplateProgrammingExerciseParticipation;
        comp.exercise = { id: 99 } as ProgrammingExercise;

        triggerChanges(comp, { property: 'participationType', currentValue: comp.participationType }, { property: 'participation', currentValue: comp.participation });
        fixture.detectChanges();

        expect(comp.latestResult).toEqual(latestResult);
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).toBeNull();
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).toBeNull();
    });

    it('should show nothing if the participation is solution and the latest result is successful', () => {
        const latestResult = { id: 3, successful: true, score: 100 } as Result;
        comp.participationType = ProgrammingExerciseParticipationType.SOLUTION;
        comp.participation = { id: 1, results: [{ id: 2, successful: false, score: 99 } as Result, latestResult] } as SolutionProgrammingExerciseParticipation;
        comp.exercise = { id: 99 } as ProgrammingExercise;
        triggerChanges(
            comp,
            { property: 'participationType', currentValue: comp.participationType, firstChange: false },
            { property: 'participation', currentValue: comp.participationType, firstChange: false },
        );
        fixture.detectChanges();
        expect(comp.latestResult).toEqual(latestResult);
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).toBeNull();
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).toBeNull();
    });

    it('should show a template warning if the participation is template and the score is > 0', () => {
        const latestResult = { id: 3, successful: false, score: 40 } as Result;
        comp.participationType = ProgrammingExerciseParticipationType.TEMPLATE;
        comp.participation = { id: 1, results: [latestResult, { id: 2, successful: false, score: 99 } as Result] } as TemplateProgrammingExerciseParticipation;
        comp.exercise = { id: 99 } as ProgrammingExercise;

        triggerChanges(
            comp,
            { property: 'participationType', currentValue: comp.participationType, firstChange: false },
            { property: 'participation', currentValue: comp.participationType, firstChange: false },
        );
        fixture.detectChanges();

        expect(comp.latestResult).toEqual(latestResult);
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).toBeDefined();
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).toBeNull();
    });

    it('should show a solution warning if the participation is solution and the result is not successful', () => {
        const latestResult = { id: 3, successful: false, score: 40 } as Result;
        comp.participationType = ProgrammingExerciseParticipationType.SOLUTION;
        comp.participation = { id: 1, results: [{ id: 2, successful: false, score: 99 } as Result, latestResult] } as SolutionProgrammingExerciseParticipation;
        comp.exercise = { id: 99 } as ProgrammingExercise;

        triggerChanges(
            comp,
            { property: 'participationType', currentValue: comp.participationType, firstChange: false },
            { property: 'participation', currentValue: comp.participationType, firstChange: false },
        );
        fixture.detectChanges();

        expect(comp.latestResult).toEqual(latestResult);
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).toBeNull();
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).toBeDefined();
    });

    it('should update the latestResult on update from the result subscription', () => {
        const newResult = { id: 4, successful: true, score: 40 } as Result;
        const latestResult = { id: 3, successful: false, score: 40 } as Result;
        comp.participationType = ProgrammingExerciseParticipationType.TEMPLATE;
        comp.participation = { id: 1, results: [latestResult, { id: 2, successful: false, score: 99 } as Result] } as TemplateProgrammingExerciseParticipation;
        comp.exercise = { id: 99 } as ProgrammingExercise;

        triggerChanges(
            comp,
            { property: 'participationType', currentValue: comp.participationType, firstChange: false },
            { property: 'participation', currentValue: comp.participationType, firstChange: false },
        );
        latestResultSubject.next(newResult);

        expect(comp.latestResult).toEqual(newResult);
    });
});

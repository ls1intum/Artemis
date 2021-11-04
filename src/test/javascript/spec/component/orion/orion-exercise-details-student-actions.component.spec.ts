import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { BehaviorSubject, of } from 'rxjs';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { OrionExerciseDetailsStudentActionsComponent } from 'app/orion/participation/orion-exercise-details-student-actions.component';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ArtemisTestModule } from '../../test.module';
import { OrionState } from 'app/shared/orion/orion';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { ActivatedRoute } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { OrionButtonComponent } from 'app/shared/orion/orion-button/orion-button.component';

describe('OrionExerciseDetailsStudentActionsComponent', () => {
    let comp: OrionExerciseDetailsStudentActionsComponent;
    let fixture: ComponentFixture<OrionExerciseDetailsStudentActionsComponent>;
    let orionConnector: OrionConnectorService;

    let ideStateStub: jest.SpyInstance;
    let cloneSpy: jest.SpyInstance;
    let submitSpy: jest.SpyInstance;
    let forwardBuildSpy: jest.SpyInstance;

    const exercise = { id: 42 } as Exercise;
    const ideState = { opened: 40, building: false, cloning: false } as OrionState;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                OrionExerciseDetailsStudentActionsComponent,
                MockComponent(ExerciseActionButtonComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(OrionButtonComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(OrionBuildAndTestService),
                MockProvider(OrionConnectorService),
                { provide: ActivatedRoute, useValue: { queryParams: of({ withIdeSubmit: true }) } },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(OrionExerciseDetailsStudentActionsComponent);
                comp = fixture.componentInstance;
                orionConnector = TestBed.inject(OrionConnectorService);
                forwardBuildSpy = jest.spyOn(TestBed.inject(OrionBuildAndTestService), 'listenOnBuildOutputAndForwardChanges');
                cloneSpy = jest.spyOn(orionConnector, 'importParticipation');
                submitSpy = jest.spyOn(orionConnector, 'submit');
                ideStateStub = jest.spyOn(orionConnector, 'state');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should not reflect that the represented exercise is opened if another exercise has been opened', fakeAsync(() => {
        const stateObservable = new BehaviorSubject(ideState);
        comp.exercise = exercise;
        ideStateStub.mockReturnValue(stateObservable);

        comp.ngOnInit();
        fixture.detectChanges();
        tick();

        expect(comp.orionState.opened).not.toBe(exercise.id);

        fixture.destroy();
        flush();
    }));
    it('should reflect that the represented exercise is opened if the same exercise is open in the IDE', fakeAsync(() => {
        const stateObservable = new BehaviorSubject({ opened: exercise.id });
        comp.exercise = exercise;
        ideStateStub.mockReturnValue(stateObservable);

        comp.ngOnInit();
        fixture.detectChanges();
        tick();

        expect(comp.orionState.opened).toBe(exercise.id);

        fixture.destroy();
        flush();
    }));
    it('should clone the correct repository in the IDE', () => {
        const participation = { id: 123, repositoryUrl: 'testUrl' } as ProgrammingExerciseStudentParticipation;
        const programmingExercise = { id: 42, title: 'Test Title' } as Exercise;
        programmingExercise.studentParticipations = [participation];
        comp.exercise = programmingExercise;
        comp.courseId = 456;

        comp.importIntoIDE();
        expect(cloneSpy).toHaveBeenCalledTimes(1);
        expect(cloneSpy).toHaveBeenCalledWith('testUrl', programmingExercise);
    });
    it('should submit the changes and then forward the build results on submit', () => {
        comp.exercise = exercise;
        comp.submitChanges();

        expect(submitSpy).toHaveBeenCalledTimes(1);
        expect(forwardBuildSpy).toHaveBeenCalledTimes(1);
        // asserts forwardBuild has been called directly after submit
        expect(forwardBuildSpy.mock.invocationCallOrder[0]).toBe(submitSpy.mock.invocationCallOrder[0] + 1);
    });
    it('isOfflineIdeAllowed should work', () => {
        comp.exercise = { allowOfflineIde: true } as any;

        expect(comp.isOfflineIdeAllowed).toBe(true);
    });
    it('should submit if stated in route', fakeAsync(() => {
        const stateObservable = new BehaviorSubject(ideState);
        comp.exercise = exercise;
        ideStateStub.mockReturnValue(stateObservable);
        const submitChangesSpy = jest.spyOn(comp, 'submitChanges');

        comp.ngOnInit();
        tick();

        expect(submitChangesSpy).toHaveBeenCalledTimes(1);
        expect(submitChangesSpy).toHaveBeenCalledWith();
    }));
});

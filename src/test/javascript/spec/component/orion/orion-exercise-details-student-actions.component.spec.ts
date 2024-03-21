import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { BehaviorSubject, of } from 'rxjs';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { OrionExerciseDetailsStudentActionsComponent } from 'app/orion/participation/orion-exercise-details-student-actions.component';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ArtemisTestModule } from '../../test.module';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { ActivatedRoute } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { OrionButtonComponent } from 'app/shared/orion/orion-button/orion-button.component';

describe('OrionExerciseDetailsStudentActionsComponent', () => {
    let comp: OrionExerciseDetailsStudentActionsComponent;
    let fixture: ComponentFixture<OrionExerciseDetailsStudentActionsComponent>;
    let orionConnectorService: OrionConnectorService;

    let orionStateStub: jest.SpyInstance;
    let cloneSpy: jest.SpyInstance;
    let submitSpy: jest.SpyInstance;
    let forwardBuildSpy: jest.SpyInstance;

    const exercise = { id: 42 } as Exercise;
    const orionState = { opened: 40, building: false, cloning: false } as any;

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
                orionConnectorService = TestBed.inject(OrionConnectorService);
                orionStateStub = jest.spyOn(orionConnectorService, 'state').mockReturnValue(new BehaviorSubject(orionState));
                forwardBuildSpy = jest.spyOn(TestBed.inject(OrionBuildAndTestService), 'listenOnBuildOutputAndForwardChanges');
                cloneSpy = jest.spyOn(orionConnectorService, 'importParticipation');
                submitSpy = jest.spyOn(orionConnectorService, 'submit');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('ngOnInit should subscribe to state', () => {
        comp.ngOnInit();

        expect(orionStateStub).toHaveBeenCalledOnce();
        expect(orionStateStub).toHaveBeenCalledWith();
        expect(comp.orionState).toEqual(orionState);
    });

    it('should clone the correct repository in the IDE', () => {
        const participation = { id: 123, repositoryUri: 'testUrl' } as ProgrammingExerciseStudentParticipation;
        const programmingExercise = { id: 42, title: 'Test Title' } as Exercise;
        programmingExercise.studentParticipations = [participation];
        comp.exercise = programmingExercise;
        comp.courseId = 456;

        comp.importIntoIDE();
        expect(cloneSpy).toHaveBeenCalledOnce();
        expect(cloneSpy).toHaveBeenCalledWith('testUrl', programmingExercise);
    });

    it('should submit the changes and then forward the build results on submit', () => {
        comp.exercise = exercise;
        comp.submitChanges();

        expect(submitSpy).toHaveBeenCalledOnce();
        expect(forwardBuildSpy).toHaveBeenCalledOnce();
        // asserts forwardBuild has been called directly after submit
        expect(forwardBuildSpy.mock.invocationCallOrder[0]).toBe(submitSpy.mock.invocationCallOrder[0] + 1);
    });

    it('isOfflineIdeAllowed should reflect exercise state', () => {
        comp.exercise = { allowOfflineIde: true } as any;

        expect(comp.isOfflineIdeAllowed).toBeTrue();
    });

    it('should submit if stated in route', () => {
        const submitChangesSpy = jest.spyOn(comp, 'submitChanges');

        comp.ngOnInit();

        expect(submitChangesSpy).toHaveBeenCalledOnce();
        expect(submitChangesSpy).toHaveBeenCalledWith();
    });
});

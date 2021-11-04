import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseComponent } from 'app/exercises/programming/manage/programming-exercise.component';
import { OrionProgrammingExerciseComponent } from 'app/orion/management/orion-programming-exercise.component';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { BehaviorSubject } from 'rxjs';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { OrionButtonComponent } from 'app/shared/orion/orion-button/orion-button.component';

describe('OrionProgrammingExerciseComponent', () => {
    let comp: OrionProgrammingExerciseComponent;
    let orionConnectorService: OrionConnectorService;

    const programmingExercise = { id: 456 } as any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [OrionProgrammingExerciseComponent, MockComponent(ProgrammingExerciseComponent), MockPipe(ArtemisTranslatePipe), MockComponent(OrionButtonComponent)],
            providers: [MockProvider(OrionConnectorService), MockProvider(ProgrammingExerciseService), MockProvider(Router)],
        })
            .compileComponents()
            .then(() => {
                comp = TestBed.createComponent(OrionProgrammingExerciseComponent).componentInstance;
                orionConnectorService = TestBed.inject(OrionConnectorService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('ngOnInit should subscribe to state', () => {
        const orionStateStub = jest.spyOn(orionConnectorService, 'state');
        const orionState = { opened: 40, building: false, cloning: false } as any;
        orionStateStub.mockReturnValue(new BehaviorSubject(orionState));

        comp.ngOnInit();

        expect(orionStateStub).toHaveBeenCalledTimes(1);
        expect(orionStateStub).toHaveBeenCalledWith();
        expect(comp.orionState).toEqual(orionState);
    });
    it('editInIde should call connector', () => {
        const editExerciseSpy = jest.spyOn(orionConnectorService, 'editExercise');
        jest.spyOn(TestBed.inject(ProgrammingExerciseService), 'find').mockReturnValue(new BehaviorSubject({ body: programmingExercise } as any));

        comp.editInIDE(programmingExercise);

        expect(editExerciseSpy).toHaveBeenCalledTimes(1);
        expect(editExerciseSpy).toHaveBeenCalledWith(programmingExercise);
    });
    it('openOrionEditor should navigate to orion editor', () => {
        const navigateSpy = jest.spyOn(TestBed.inject(Router), 'navigate');
        comp.openOrionEditor({ ...programmingExercise, templateParticipation: { id: 1234 } });

        expect(navigateSpy).toHaveBeenCalledTimes(1);
        expect(navigateSpy).toHaveBeenCalledWith(['code-editor', 'ide', 456, 'admin', 1234]);
    });
    it('openOrionEditor with error', () => {
        const navigateStub = jest.spyOn(TestBed.inject(Router), 'navigate').mockImplementation(() => {
            throw 'test error';
        });

        comp.openOrionEditor(programmingExercise);

        expect(navigateStub).toHaveBeenCalledTimes(1);
        expect(navigateStub).toHaveBeenCalledWith(['code-editor', 'ide', 456, 'admin', undefined]);
    });
});

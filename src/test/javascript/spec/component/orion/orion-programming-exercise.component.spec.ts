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
    let router: Router;
    let programmingExerciseService: ProgrammingExerciseService;

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
                router = TestBed.inject(Router);
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('ngOnInit should subscribe to state', () => {
        const orionState = { opened: 40, building: false, cloning: false } as any;
        const orionStateStub = jest.spyOn(orionConnectorService, 'state').mockReturnValue(new BehaviorSubject(orionState));

        comp.ngOnInit();

        expect(orionStateStub).toHaveBeenCalledOnce();
        expect(orionStateStub).toHaveBeenCalledWith();
        expect(comp.orionState).toEqual(orionState);
    });

    it('editInIde should call connector', () => {
        const editExerciseSpy = jest.spyOn(orionConnectorService, 'editExercise');
        jest.spyOn(programmingExerciseService, 'find').mockReturnValue(new BehaviorSubject({ body: programmingExercise } as any));

        comp.editInIDE(programmingExercise);

        expect(editExerciseSpy).toHaveBeenCalledOnce();
        expect(editExerciseSpy).toHaveBeenCalledWith(programmingExercise);
    });

    it('openOrionEditor should navigate to orion editor', () => {
        const navigateSpy = jest.spyOn(router, 'navigate');
        comp.openOrionEditor({ ...programmingExercise, templateParticipation: { id: 1234 } });

        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['code-editor', 'ide', 456, 'admin', 1234]);
    });

    it('openOrionEditor should handle error', () => {
        const navigateStub = jest.spyOn(router, 'navigate').mockImplementation(() => {
            throw 'test error';
        });

        comp.openOrionEditor(programmingExercise);

        expect(navigateStub).toHaveBeenCalledOnce();
        expect(navigateStub).toHaveBeenCalledWith(['code-editor', 'ide', 456, 'admin', undefined]);
    });
});

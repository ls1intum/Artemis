import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { HttpResponse } from '@angular/common/http';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ExampleSolutionComponent } from 'app/exercises/shared/example-solution/example-solution.component';
import { ExampleSolutionInfo, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Exercise } from 'app/entities/exercise.model';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercises/shared/exercise-headers/header-exercise-page-with-details.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

describe('Example Solution Component', () => {
    let comp: ExampleSolutionComponent;
    let fixture: ComponentFixture<ExampleSolutionComponent>;
    let exerciseService: ExerciseService;
    let artemisMarkdownService: ArtemisMarkdownService;

    const exercise = {
        id: 10,
        exampleSolution: 'Example Solution',
    } as TextExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ExampleSolutionComponent,
                MockComponent(HeaderExercisePageWithDetailsComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(HtmlForMarkdownPipe),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ exerciseId: 10 }),
                },
                MockProvider(ExerciseService),
                MockProvider(ArtemisMarkdownService),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ExampleSolutionComponent);
        comp = fixture.componentInstance;
        exerciseService = fixture.debugElement.injector.get(ExerciseService);
        artemisMarkdownService = fixture.debugElement.injector.get(ArtemisMarkdownService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const exerciseServiceSpy = jest.spyOn(exerciseService, 'getExerciseForExampleSolution').mockReturnValue(of({ body: exercise } as HttpResponse<Exercise>));
        const exampleSolutionInfo = { programmingExercise: { id: 1, exampleSolutionPublicationDate: dayjs().subtract(1, 'm') } } as ExampleSolutionInfo;

        const extractExampleSolutionInfoSpy = jest.spyOn(ExerciseService, 'extractExampleSolutionInfo').mockReturnValue(exampleSolutionInfo);
        fixture.detectChanges();
        expect(exerciseServiceSpy).toHaveBeenCalledOnce();
        expect(exerciseServiceSpy).toHaveBeenCalledWith(exercise.id);

        expect(extractExampleSolutionInfoSpy).toHaveBeenCalledOnce();
        expect(extractExampleSolutionInfoSpy).toHaveBeenCalledWith(exercise, artemisMarkdownService);

        expect(comp.exampleSolutionInfo).toEqual(exampleSolutionInfo);
    });
});

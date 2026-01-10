import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { HttpResponse } from '@angular/common/http';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ExampleSolutionInfo, ExerciseService } from 'app/exercise/services/exercise.service';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercise/exercise-headers/with-details/header-exercise-page-with-details.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ExampleSolutionComponent } from 'app/exercise/example-solution/example-solution.component';

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
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ExampleSolutionComponent);
        comp = fixture.componentInstance;
        exerciseService = TestBed.inject(ExerciseService);
        artemisMarkdownService = TestBed.inject(ArtemisMarkdownService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const exerciseServiceSpy = jest.spyOn(exerciseService, 'getExerciseForExampleSolution').mockReturnValue(of({ body: exercise } as HttpResponse<Exercise>));
        const exampleSolutionInfo = { programmingExercise: { id: 1, exampleSolutionPublicationDate: dayjs().subtract(1, 'm') } } as ExampleSolutionInfo;

        const extractExampleSolutionInfoSpy = jest.spyOn(ExerciseService, 'extractExampleSolutionInfo').mockReturnValue(exampleSolutionInfo);
        fixture.componentRef.setInput('exerciseId', 10);
        fixture.detectChanges();
        expect(exerciseServiceSpy).toHaveBeenCalledOnce();
        expect(exerciseServiceSpy).toHaveBeenCalledWith(exercise.id);

        expect(extractExampleSolutionInfoSpy).toHaveBeenCalledOnce();
        expect(extractExampleSolutionInfoSpy).toHaveBeenCalledWith(exercise, artemisMarkdownService);

        expect(comp.exampleSolutionInfo).toEqual(exampleSolutionInfo);
    });
});

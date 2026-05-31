import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { ExampleSolutionComponent } from 'app/exercise/example-solution/example-solution.component';
import { ExampleSolutionInfo, ExerciseService } from 'app/exercise/services/exercise.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Example Solution Component', () => {
    setupTestBed({ zoneless: true });

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
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ exerciseId: 10 }),
                },
                MockProvider(ExerciseService),
                MockProvider(ArtemisMarkdownService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(ExampleSolutionComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(ExampleSolutionComponent);
        comp = fixture.componentInstance;
        exerciseService = TestBed.inject(ExerciseService);
        artemisMarkdownService = TestBed.inject(ArtemisMarkdownService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        const exerciseServiceSpy = vi.spyOn(exerciseService, 'getExerciseForExampleSolution').mockReturnValue(of({ body: exercise } as HttpResponse<Exercise>));
        const exampleSolutionInfo = { programmingExercise: { id: 1, exampleSolutionPublicationDate: dayjs().subtract(1, 'm') } } as ExampleSolutionInfo;

        const extractExampleSolutionInfoSpy = vi.spyOn(ExerciseService, 'extractExampleSolutionInfo').mockReturnValue(exampleSolutionInfo);
        fixture.detectChanges();
        expect(exerciseServiceSpy).toHaveBeenCalledOnce();
        expect(exerciseServiceSpy).toHaveBeenCalledWith(exercise.id);

        expect(extractExampleSolutionInfoSpy).toHaveBeenCalledOnce();
        expect(extractExampleSolutionInfoSpy).toHaveBeenCalledWith(exercise, artemisMarkdownService);

        expect(comp.exampleSolutionInfo).toEqual(exampleSolutionInfo);
    });
});

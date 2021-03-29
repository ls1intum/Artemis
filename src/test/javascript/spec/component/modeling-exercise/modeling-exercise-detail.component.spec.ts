import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { ModelingExerciseDetailComponent } from 'app/exercises/modeling/manage/modeling-exercise-detail.component';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';

describe('ModelingExercise Management Detail Component', () => {
    let comp: ModelingExerciseDetailComponent;
    let fixture: ComponentFixture<ModelingExerciseDetailComponent>;
    let modelingExerciseService: ModelingExerciseService;

    const modelingExercise = { id: 123 } as ModelingExercise;
    const route = ({ params: of({ exerciseId: modelingExercise.id }) } as any) as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ModelingExerciseDetailComponent, MockComponent(NonProgrammingExerciseDetailCommonActionsComponent)],
            providers: [{ provide: ActivatedRoute, useValue: route }, MockProvider(TranslateService)],
        })
            .overrideTemplate(ModelingExerciseDetailComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(ModelingExerciseDetailComponent);
        comp = fixture.componentInstance;
        modelingExerciseService = fixture.debugElement.injector.get(ModelingExerciseService);
    });

    it('Should load exercise on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        spyOn(modelingExerciseService, 'find').and.returnValue(
            of(
                new HttpResponse({
                    body: modelingExercise,
                    headers,
                }),
            ),
        );

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(modelingExerciseService.find).toHaveBeenCalled();
        expect(comp.modelingExercise).toEqual(modelingExercise);
    });
});

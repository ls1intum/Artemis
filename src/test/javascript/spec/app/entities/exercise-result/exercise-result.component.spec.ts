import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArtemisTestModule } from '../../../test.module';
import { ExerciseResultComponent } from 'app/entities/exercise-result/exercise-result.component';
import { ExerciseResultService } from 'app/entities/exercise-result/exercise-result.service';
import { ExerciseResult } from 'app/shared/model/exercise-result.model';

describe('Component Tests', () => {
    describe('ExerciseResult Management Component', () => {
        let comp: ExerciseResultComponent;
        let fixture: ComponentFixture<ExerciseResultComponent>;
        let service: ExerciseResultService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [ExerciseResultComponent],
            })
                .overrideTemplate(ExerciseResultComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ExerciseResultComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ExerciseResultService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ExerciseResult(123)],
                        headers,
                    }),
                ),
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.exerciseResults && comp.exerciseResults[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});

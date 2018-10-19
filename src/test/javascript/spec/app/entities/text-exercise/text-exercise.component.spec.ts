/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { TextExerciseComponent } from 'app/entities/text-exercise/text-exercise.component';
import { TextExerciseService } from 'app/entities/text-exercise/text-exercise.service';
import { TextExercise } from 'app/shared/model/text-exercise.model';

describe('Component Tests', () => {
    describe('TextExercise Management Component', () => {
        let comp: TextExerciseComponent;
        let fixture: ComponentFixture<TextExerciseComponent>;
        let service: TextExerciseService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [TextExerciseComponent],
                providers: []
            })
                .overrideTemplate(TextExerciseComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(TextExerciseComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(TextExerciseService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new TextExercise(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.textExercises[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});

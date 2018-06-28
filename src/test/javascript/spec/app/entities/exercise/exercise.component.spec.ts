/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ExerciseComponent } from '../../../../../../main/webapp/app/entities/exercise/exercise.component';
import { ExerciseService } from '../../../../../../main/webapp/app/entities/exercise/exercise.service';
import { Exercise } from '../../../../../../main/webapp/app/entities/exercise/exercise.model';

describe('Component Tests', () => {

    describe('Exercise Management Component', () => {
        let comp: ExerciseComponent;
        let fixture: ComponentFixture<ExerciseComponent>;
        let service: ExerciseService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ExerciseComponent],
                providers: [
                    ExerciseService
                ]
            })
            .overrideTemplate(ExerciseComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ExerciseComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ExerciseService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new Exercise(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.exercises[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});

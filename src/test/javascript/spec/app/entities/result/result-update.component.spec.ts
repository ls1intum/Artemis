/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ResultUpdateComponent } from 'app/entities/result/result-update.component';
import { ResultService } from 'app/entities/result/result.service';
import { Result } from 'app/shared/model/result.model';

describe('Component Tests', () => {
    describe('Result Management Update Component', () => {
        let comp: ResultUpdateComponent;
        let fixture: ComponentFixture<ResultUpdateComponent>;
        let service: ResultService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ResultUpdateComponent]
            })
                .overrideTemplate(ResultUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ResultUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ResultService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new Result(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.result = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.update).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );

            it(
                'Should call create service on save for new entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new Result();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.result = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.create).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );
        });
    });
});

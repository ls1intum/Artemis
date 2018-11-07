/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerMappingUpdateComponent } from 'app/entities/short-answer-mapping/short-answer-mapping-update.component';
import { ShortAnswerMappingService } from 'app/entities/short-answer-mapping/short-answer-mapping.service';
import { ShortAnswerMapping } from 'app/entities/short-answer-mapping/short-answer-mapping.model';

describe('Component Tests', () => {
    describe('ShortAnswerMapping Management Update Component', () => {
        let comp: ShortAnswerMappingUpdateComponent;
        let fixture: ComponentFixture<ShortAnswerMappingUpdateComponent>;
        let service: ShortAnswerMappingService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerMappingUpdateComponent]
            })
                .overrideTemplate(ShortAnswerMappingUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerMappingUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerMappingService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerMapping(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerMapping = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerMapping();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerMapping = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));
        });
    });
});

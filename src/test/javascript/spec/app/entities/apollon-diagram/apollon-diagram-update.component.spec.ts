/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ApollonDiagramUpdateComponent } from 'app/entities/apollon-diagram/apollon-diagram-update.component';
import { ApollonDiagramService } from 'app/entities/apollon-diagram/apollon-diagram.service';
import { ApollonDiagram } from 'app/shared/model/apollon-diagram.model';

describe('Component Tests', () => {
    describe('ApollonDiagram Management Update Component', () => {
        let comp: ApollonDiagramUpdateComponent;
        let fixture: ComponentFixture<ApollonDiagramUpdateComponent>;
        let service: ApollonDiagramService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ApollonDiagramUpdateComponent]
            })
                .overrideTemplate(ApollonDiagramUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ApollonDiagramUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ApollonDiagramService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new ApollonDiagram(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.apollonDiagram = entity;
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
                    const entity = new ApollonDiagram();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.apollonDiagram = entity;
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

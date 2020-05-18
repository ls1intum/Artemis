import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { ModelingExerciseUpdateComponent } from 'app/exercises/modeling/manage/modeling-exercise-update.component';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

describe('ModelingExercise Management Update Component', () => {
    let comp: ModelingExerciseUpdateComponent;
    let fixture: ComponentFixture<ModelingExerciseUpdateComponent>;
    let service: ModelingExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ModelingExerciseUpdateComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .overrideTemplate(ModelingExerciseUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(ModelingExerciseService);
    });

    describe('save', () => {
        it('Should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new ModelingExercise(UMLDiagramType.ActivityDiagram);
            entity.id = 123;
            spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.modelingExercise = entity;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.update).toHaveBeenCalledWith(entity, {});
            expect(comp.isSaving).toEqual(false);
        }));

        it('Should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new ModelingExercise(UMLDiagramType.ClassDiagram);
            spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.modelingExercise = entity;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.create).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toEqual(false);
        }));
    });
});

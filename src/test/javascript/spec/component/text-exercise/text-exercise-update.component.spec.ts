import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { TextExerciseUpdateComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-update.component';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';

describe('TextExercise Management Update Component', () => {
    let comp: TextExerciseUpdateComponent;
    let fixture: ComponentFixture<TextExerciseUpdateComponent>;
    let service: TextExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
            declarations: [TextExerciseUpdateComponent],
        })
            .overrideTemplate(TextExerciseUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(TextExerciseUpdateComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(TextExerciseService);
    });

    describe('save', () => {
        it('Should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new TextExercise();
            entity.id = 123;
            spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.textExercise = entity;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.update).toHaveBeenCalledWith(entity, {});
            expect(comp.isSaving).toEqual(false);
        }));

        it('Should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new TextExercise();
            spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.textExercise = entity;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.create).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toEqual(false);
        }));
    });
});

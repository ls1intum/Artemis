import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { FormBuilder } from '@angular/forms';
import { of } from 'rxjs';

import { TextHintUpdateComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-update.component';
import { ArtemisTestModule } from '../../test.module';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { TextHint } from 'app/entities/hestia/text-hint-model';
import { TextHintService } from 'app/exercises/shared/exercise-hint/manage/text-hint.service';

describe('ExerciseHint Management Update Component', () => {
    let comp: TextHintUpdateComponent;
    let fixture: ComponentFixture<TextHintUpdateComponent>;
    let service: TextHintService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TextHintUpdateComponent],
            providers: [FormBuilder, MockProvider(TranslateService)],
        })
            .overrideTemplate(TextHintUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(TextHintUpdateComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(TextHintService);
    });

    describe('save', () => {
        it('Should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new TextHint();
            entity.id = 123;
            jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.textHint = entity;
            comp.courseId = 1;
            comp.exerciseId = 2;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.update).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toEqual(false);
        }));

        it('Should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new TextHint();
            jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.textHint = entity;
            comp.courseId = 1;
            comp.exerciseId = 2;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.create).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toEqual(false);
        }));
    });
});

import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import * as moment from 'moment';

import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/update/programming-exercise-update.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';

describe('ProgrammingExercise Management Update Component', () => {
    let comp: ProgrammingExerciseUpdateComponent;
    let fixture: ComponentFixture<ProgrammingExerciseUpdateComponent>;
    let service: ProgrammingExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseUpdateComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(ProgrammingExerciseUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(ProgrammingExerciseService);
    });

    describe('save', () => {
        it('Should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(new Course());
            entity.id = 123;
            entity.releaseDate = moment(); // We will get a warning if we do not set a release date
            spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.programmingExercise = entity;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.update).toHaveBeenCalledWith(entity, {});
            expect(comp.isSaving).toEqual(false);
        }));

        it('Should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise();
            entity.releaseDate = moment(); // We will get a warning if we do not set a release date
            spyOn(service, 'automaticSetup').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.programmingExercise = entity;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.automaticSetup).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toEqual(false);
        }));
    });
});

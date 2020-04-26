import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { FileUploadExerciseUpdateComponent } from 'app/exercises/file-upload/manage/file-upload-exercise-update.component';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockFileUploadExerciseService } from '../../helpers/mocks/service/mock-file-upload-exercise.service';

describe('FileUploadExercise Management Update Component', () => {
    let comp: FileUploadExerciseUpdateComponent;
    let fixture: ComponentFixture<FileUploadExerciseUpdateComponent>;
    let service: FileUploadExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [FileUploadExerciseUpdateComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: FileUploadExerciseService, useClass: MockFileUploadExerciseService },
            ],
        })
            .overrideTemplate(FileUploadExerciseUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(FileUploadExerciseService);
    });

    describe('save', () => {
        it('Should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new FileUploadExercise();
            entity.id = 123;
            spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.fileUploadExercise = entity;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.update).toHaveBeenCalledWith(entity, 123);
            expect(comp.isSaving).toEqual(false);
        }));

        it('Should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new FileUploadExercise();
            spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.fileUploadExercise = entity;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.create).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toEqual(false);
        }));
    });
});

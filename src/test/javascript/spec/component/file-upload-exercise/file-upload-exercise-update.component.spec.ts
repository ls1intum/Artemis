import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { FileUploadExerciseUpdateComponent } from 'app/exercises/file-upload/manage/file-upload-exercise-update.component';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockFileUploadExerciseService } from '../../helpers/mocks/service/mock-file-upload-exercise.service';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Course } from 'app/entities/course.model';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';

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
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                MockProvider(TranslateService),
            ],
        })
            .overrideTemplate(FileUploadExerciseUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(FileUploadExerciseService);
    });

    describe('save', () => {
        describe('new exercise', () => {
            const course = { id: 1 } as Course;
            const fileUploadExercise = new FileUploadExercise(course, undefined);

            beforeEach(() => {
                const route = TestBed.inject(ActivatedRoute);
                route.data = of({ fileUploadExercise });
            });

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                comp.ngOnInit();

                const entity = { ...fileUploadExercise };
                jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));

                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toBeFalse();
            }));
        });

        describe('existing exercise', () => {
            const course = { id: 1 } as Course;
            const fileUploadExercise = new FileUploadExercise(course, undefined);
            fileUploadExercise.id = 123;

            beforeEach(() => {
                const route = TestBed.inject(ActivatedRoute);
                route.data = of({ fileUploadExercise });
            });

            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = { ...fileUploadExercise };
                jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
                comp.ngOnInit();

                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity, {});
                expect(comp.isSaving).toBeFalse();
            }));
        });
    });

    describe('ngOnInit with given exerciseGroup', () => {
        const fileUploadExercise = new FileUploadExercise(undefined, new ExerciseGroup());

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.data = of({ fileUploadExercise });
        });

        it('Should be in exam mode', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isExamMode).toBeTrue();
            expect(comp.fileUploadExercise).toEqual(fileUploadExercise);
        }));
    });

    describe('ngOnInit without given exerciseGroup', () => {
        const fileUploadExercise = new FileUploadExercise(new Course(), undefined);

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.data = of({ fileUploadExercise });
        });

        it('Should not be in exam mode', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isExamMode).toBeFalse();
            expect(comp.fileUploadExercise).toEqual(fileUploadExercise);
        }));
    });
});

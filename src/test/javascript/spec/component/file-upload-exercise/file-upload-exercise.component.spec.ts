import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArtemisTestModule } from '../../test.module';
import { FileUploadExerciseComponent } from 'app/exercises/file-upload/manage/file-upload-exercise.component';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';

describe('FileUploadExercise Management Component', () => {
    let comp: FileUploadExerciseComponent;
    let fixture: ComponentFixture<FileUploadExerciseComponent>;
    let service: FileUploadExerciseService;
    const course: Course = { id: 123 } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [FileUploadExerciseComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(FileUploadExerciseComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(FileUploadExerciseComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(FileUploadExerciseService);
    });

    it('Should call load all on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        spyOn(service, 'query').and.returnValue(
            of(
                new HttpResponse({
                    body: [new FileUploadExercise(course)],
                    headers,
                }),
            ),
        );

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(service.query).toHaveBeenCalled();
        expect(comp.fileUploadExercises[0]).toEqual(jasmine.objectContaining({ id: 123 }));
    });
});

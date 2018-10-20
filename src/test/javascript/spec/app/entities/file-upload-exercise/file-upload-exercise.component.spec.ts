/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { FileUploadExerciseComponent } from 'app/entities/file-upload-exercise/file-upload-exercise.component';
import { FileUploadExerciseService } from 'app/entities/file-upload-exercise/file-upload-exercise.service';
import { FileUploadExercise } from 'app/shared/model/file-upload-exercise.model';

describe('Component Tests', () => {
    describe('FileUploadExercise Management Component', () => {
        let comp: FileUploadExerciseComponent;
        let fixture: ComponentFixture<FileUploadExerciseComponent>;
        let service: FileUploadExerciseService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [FileUploadExerciseComponent],
                providers: []
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
                        body: [new FileUploadExercise(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.fileUploadExercises[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});

/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { FileUploadExerciseComponent } from '../../../../../../main/webapp/app/entities/file-upload-exercise/file-upload-exercise.component';
import { FileUploadExerciseService } from '../../../../../../main/webapp/app/entities/file-upload-exercise/file-upload-exercise.service';
import { FileUploadExercise } from '../../../../../../main/webapp/app/entities/file-upload-exercise/file-upload-exercise.model';

describe('Component Tests', () => {

    describe('FileUploadExercise Management Component', () => {
        let comp: FileUploadExerciseComponent;
        let fixture: ComponentFixture<FileUploadExerciseComponent>;
        let service: FileUploadExerciseService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [FileUploadExerciseComponent],
                providers: [
                    FileUploadExerciseService
                ]
            })
            .overrideTemplate(FileUploadExerciseComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(FileUploadExerciseComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(FileUploadExerciseService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new FileUploadExercise(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.fileUploadExercises[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});

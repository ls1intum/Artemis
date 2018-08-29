/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { FileUploadExerciseDetailComponent } from '../../../../../../main/webapp/app/entities/file-upload-exercise/file-upload-exercise-detail.component';
import { FileUploadExerciseService } from '../../../../../../main/webapp/app/entities/file-upload-exercise/file-upload-exercise.service';
import { FileUploadExercise } from '../../../../../../main/webapp/app/entities/file-upload-exercise/file-upload-exercise.model';

describe('Component Tests', () => {

    describe('FileUploadExercise Management Detail Component', () => {
        let comp: FileUploadExerciseDetailComponent;
        let fixture: ComponentFixture<FileUploadExerciseDetailComponent>;
        let service: FileUploadExerciseService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [FileUploadExerciseDetailComponent],
                providers: [
                    FileUploadExerciseService
                ]
            })
            .overrideTemplate(FileUploadExerciseDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(FileUploadExerciseService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new FileUploadExercise(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.fileUploadExercise).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});

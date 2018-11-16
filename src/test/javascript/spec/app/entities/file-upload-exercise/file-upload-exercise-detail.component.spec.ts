/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { FileUploadExerciseDetailComponent } from 'app/entities/file-upload-exercise/file-upload-exercise-detail.component';
import { FileUploadExercise } from 'app/shared/model/file-upload-exercise.model';

describe('Component Tests', () => {
    describe('FileUploadExercise Management Detail Component', () => {
        let comp: FileUploadExerciseDetailComponent;
        let fixture: ComponentFixture<FileUploadExerciseDetailComponent>;
        const route = ({ data: of({ fileUploadExercise: new FileUploadExercise(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [FileUploadExerciseDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(FileUploadExerciseDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.fileUploadExercise).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});

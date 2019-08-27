/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArtemisTestModule } from '../../test.module';
import { FileUploadExerciseComponent } from 'app/entities/file-upload-exercise/file-upload-exercise.component';
import { FileUploadExerciseService } from 'app/entities/file-upload-exercise/file-upload-exercise.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise/file-upload-exercise.model';
import { FileUploadExerciseDeleteDialogComponent } from 'app/entities/file-upload-exercise/file-upload-exercise-delete-dialog.component';
import { FileUploadExerciseDetailComponent } from 'app/entities/file-upload-exercise/file-upload-exercise-detail.component';
import { FileUploadExerciseUpdateComponent } from 'app/entities/file-upload-exercise/file-upload-exercise-update.component';
import { FileUploadExercisePopupService } from 'app/entities/file-upload-exercise/file-upload-exercise-popup.service';
import { fileUploadExerciseRoute } from 'app/entities/file-upload-exercise/file-upload-exercise.route';

import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared';
import { RouterTestingModule } from '@angular/router/testing';
import { AccountService, JhiLanguageHelper } from 'app/core';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { MockAccountService } from '../../mocks/mock-account.service';
import { MockFileUploadExerciseService } from '../../mocks/mock-file-upload-exercise.service';
import { JhiAlertService } from 'ng-jhipster';
import { Course } from 'app/entities/course';

describe('FileUploadExerciseComponent', () => {
    let comp: FileUploadExerciseComponent;
    let fixture: ComponentFixture<FileUploadExerciseComponent>;
    let service: FileUploadExerciseService;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, RouterTestingModule.withRoutes([fileUploadExerciseRoute[0]])],
            declarations: [
                FileUploadExerciseComponent,
                MockComponent(FileUploadExerciseDetailComponent),
                MockComponent(FileUploadExerciseUpdateComponent),
                MockComponent(FileUploadExerciseDeleteDialogComponent),
            ],
            providers: [
                JhiLanguageHelper,
                { provide: JhiAlertService, useClass: MockAlertService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: FileUploadExerciseService, useClass: MockFileUploadExerciseService },
                FileUploadExercisePopupService,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadExerciseComponent);
                comp = fixture.componentInstance;
                service = fixture.debugElement.injector.get(FileUploadExerciseService);
            });
    });

    it('Should call load all on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        spyOn(service, 'query').and.returnValue(
            of(
                new HttpResponse({
                    body: [new FileUploadExercise(new Course())],
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

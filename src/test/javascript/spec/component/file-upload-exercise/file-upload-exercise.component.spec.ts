import { ComponentFixture, TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import * as chai from 'chai';

import { ArtemisTestModule } from '../../test.module';
import { FileUploadExerciseComponent } from 'app/entities/file-upload-exercise/file-upload-exercise.component';
import { FileUploadExerciseService } from 'app/entities/file-upload-exercise/file-upload-exercise.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise/file-upload-exercise.model';
import { FileUploadExerciseDeleteDialogComponent } from 'app/entities/file-upload-exercise/file-upload-exercise-delete-dialog.component';
import { FileUploadExerciseDetailComponent } from 'app/entities/file-upload-exercise/file-upload-exercise-detail.component';
import { FileUploadExerciseUpdateComponent } from 'app/entities/file-upload-exercise/file-upload-exercise-update.component';
import { FileUploadExercisePopupService } from 'app/entities/file-upload-exercise/file-upload-exercise-popup.service';
import { fileUploadExerciseRoute } from 'app/entities/file-upload-exercise/file-upload-exercise.route';

import { Location } from '@angular/common';
import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared';
import { RouterTestingModule } from '@angular/router/testing';
import { AccountService } from 'app/core';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { MockAccountService } from '../../mocks/mock-account.service';
import { MockFileUploadExerciseService } from '../../mocks/mock-file-upload-exercise.service';
import { JhiAlertService } from 'ng-jhipster';
import { ExerciseType } from 'app/entities/exercise';
import { SortByModule } from 'app/components/pipes';
import { Router } from '@angular/router';
import { DebugElement } from '@angular/core';
import { FileUploadSubmission, FileUploadSubmissionService } from 'app/entities/file-upload-submission';
import { By } from '@angular/platform-browser';
import * as sinonChai from 'sinon-chai';
import { MockSyncStorage } from '../../mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { DifferencePipe } from 'ngx-moment';
import { Course, CourseExerciseService, CourseService } from 'app/entities/course';
import { of } from 'rxjs';
import { MockCourseService } from '../../mocks/mock-course.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('FileUploadExerciseComponent', () => {
    let comp: FileUploadExerciseComponent;
    let fixture: ComponentFixture<FileUploadExerciseComponent>;
    let debugElement: DebugElement;
    let router: Router;
    let location: Location;

    const exercises = [{ id: 20, type: ExerciseType.FILE_UPLOAD }, { id: 20, type: ExerciseType.FILE_UPLOAD }] as FileUploadExercise[];

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, SortByModule, RouterTestingModule],
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
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: DifferencePipe, useClass: DifferencePipe },
                {
                    provide: CourseExerciseService,
                    useValue: {
                        findAllFileUploadExercisesForCourse: (courseId: number) => {
                            of(exercises);
                        },
                    },
                },
                { provide: CourseService, useClass: MockCourseService },
                FileUploadExercisePopupService,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadExerciseComponent);
                comp = fixture.componentInstance;
                comp.fileUploadExercises = exercises;
                debugElement = fixture.debugElement;
                router = debugElement.injector.get(Router);
                location = debugElement.injector.get(Location);

                router.initialNavigation();
            });
    });

    it('Table should be visible', fakeAsync(() => {
        // set all attributes for comp
        tick();
        fixture.detectChanges();

        // check if table is shown
        const table = debugElement.query(By.css('.table.table-stripe'));
        expect(table).to.exist;

        // check if getTextSubmissionForExerciseWithoutAssessment() is called and works
        // assessNextButton.nativeElement.click();
        // expect(getFileUploadSubmissionForExerciseWithoutAssessmentStub).to.have.been.called;
        //expect(comp.unassessedSubmission).to.be.deep.equal(unassessedSubmission);

        // check if the url changes when you clicked on assessNextAssessmentButton
        //expect(location.path()).to.be.equal('/file-upload-exercise/' + comp.exercise.id + '/submission/' + comp.unassessedSubmission.id + '/assessment');

        fixture.destroy();
        flush();
    }));
});

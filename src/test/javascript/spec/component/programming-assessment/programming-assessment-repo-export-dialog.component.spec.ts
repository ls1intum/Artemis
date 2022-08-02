import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-dialog.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ProgrammingAssessmentRepoExportService } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export.service';
import { Exercise } from 'app/entities/exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateValuesDirective } from '../../helpers/mocks/directive/mock-translate-values.directive';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NgForm, NgModel } from '@angular/forms';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';

const createBlobHttpResponse = () => {
    const blob = new Blob([JSON.stringify({ property: 'blob' })], { type: 'application/json' });
    const headers = new HttpHeaders().set('filename', 'blob file');
    return new HttpResponse({ body: blob, headers });
};

describe('ProgrammingAssessmentRepoExportDialogComponent', () => {
    let comp: ProgrammingAssessmentRepoExportDialogComponent;
    let fixture: ComponentFixture<ProgrammingAssessmentRepoExportDialogComponent>;
    let exerciseService: ExerciseService;
    let repoExportService: ProgrammingAssessmentRepoExportService;

    global.URL.createObjectURL = jest.fn(() => 'http://some.test.com');
    global.URL.revokeObjectURL = jest.fn(() => '');

    const exerciseId = 42;
    const participationIdList = [1];
    const singleParticipantMode = false;
    const programmingExercise = new ProgrammingExercise(new Course(), undefined);
    programmingExercise.id = exerciseId;
    programmingExercise.releaseDate = dayjs();
    programmingExercise.dueDate = dayjs().add(7, 'days');

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ProgrammingAssessmentRepoExportDialogComponent,
                MockTranslateValuesDirective,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FormDateTimePickerComponent),
                MockComponent(FaIconComponent),
                MockDirective(TranslateDirective),
                MockDirective(FeatureToggleDirective),
                MockDirective(NgModel),
                MockDirective(NgForm),
                MockDirective(MockHasAnyAuthorityDirective),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                // Ignore console errors
                console.error = () => false;
                fixture = TestBed.createComponent(ProgrammingAssessmentRepoExportDialogComponent);
                comp = fixture.componentInstance;
                exerciseService = fixture.debugElement.injector.get(ExerciseService);
                repoExportService = fixture.debugElement.injector.get(ProgrammingAssessmentRepoExportService);

                // stubs
                jest.spyOn(exerciseService, 'find').mockReturnValue(of({ body: programmingExercise } as HttpResponse<Exercise>));

                comp.exerciseId = exerciseId;
                comp.participationIdList = participationIdList;
                comp.singleParticipantMode = singleParticipantMode;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('test initialization', () => {
        fixture.detectChanges();
        expect(comp.exerciseId).toBe(42);
    });

    it('Exercise service should find the correct programming exercise', () => {
        fixture.detectChanges();
        expect(comp.exercise).toEqual(programmingExercise);
    });

    it('Export a repo by participations should download a zipped file', fakeAsync(() => {
        const httpResponse = createBlobHttpResponse();
        const exportReposStub = jest.spyOn(repoExportService, 'exportReposByParticipations').mockReturnValue(of(httpResponse));
        fixture.detectChanges();

        comp.exportRepos(exerciseId);
        tick();
        expect(comp.repositoryExportOptions.addParticipantName).toBeFalse();
        expect(comp.repositoryExportOptions.hideStudentNameInZippedFolder).toBeTrue();
        expect(comp.exportInProgress).toBeFalse();
        expect(exportReposStub).toHaveBeenCalledOnce();
    }));

    it('Export a repo by participant identifiers should download a zipped file', fakeAsync(() => {
        comp.participationIdList = [];
        comp.participantIdentifierList = 'ALL';
        const httpResponse = createBlobHttpResponse();
        const exportReposStub = jest.spyOn(repoExportService, 'exportReposByParticipantIdentifiers').mockReturnValue(of(httpResponse));
        fixture.detectChanges();

        comp.exportRepos(exerciseId);
        tick();
        expect(comp.repositoryExportOptions.addParticipantName).toBeTrue();
        expect(comp.exportInProgress).toBeFalse();
        expect(exportReposStub).toHaveBeenCalledOnce();
    }));

    it('Export of multiple repos download multiple files', fakeAsync(() => {
        const programmingExercise2 = new ProgrammingExercise(new Course(), undefined);
        programmingExercise2.id = 43;
        fixture.componentInstance.exercise = programmingExercise2;
        fixture.componentInstance.exercise.isAtLeastInstructor = true;
        comp.selectedProgrammingExercises = [programmingExercise, programmingExercise2];
        const httpResponse = createBlobHttpResponse();
        const exportReposStub = jest.spyOn(repoExportService, 'exportReposByParticipations').mockReturnValue(of(httpResponse));
        fixture.detectChanges();

        comp.bulkExportRepos();
        tick();
        expect(comp.repositoryExportOptions.exportAllParticipants).toBeTrue();
        expect(comp.exportInProgress).toBeFalse();
        expect(exportReposStub).toHaveBeenCalledTimes(2);
    }));
});

import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/programming/manage/assess/repo-export/export-dialog/programming-assessment-repo-export-dialog.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ProgrammingAssessmentRepoExportService } from 'app/programming/manage/assess/repo-export/programming-assessment-repo-export.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { provideHttpClientTesting } from '@angular/common/http/testing';

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
            imports: [FormDateTimePickerComponent, OwlNativeDateTimeModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                LocalStorageService,
                MockProvider(NgbActiveModal),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                // Ignore console errors
                console.error = () => false;
                fixture = TestBed.createComponent(ProgrammingAssessmentRepoExportDialogComponent);
                comp = fixture.componentInstance;
                exerciseService = TestBed.inject(ExerciseService);
                repoExportService = TestBed.inject(ProgrammingAssessmentRepoExportService);

                // stubs
                jest.spyOn(exerciseService, 'find').mockReturnValue(of({ body: programmingExercise } as HttpResponse<Exercise>));

                comp.programmingExercises = [programmingExercise];
                comp.participationIdList = participationIdList;
                comp.singleParticipantMode = singleParticipantMode;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('test initialization', () => {
        fixture.detectChanges();
        expect(comp.programmingExercises[0].id).toBe(42);
    });

    it('Exercise service should find the correct programming exercise', () => {
        fixture.detectChanges();
        expect(comp.programmingExercises[0]).toEqual(programmingExercise);
    });

    it('Export a repo by participations should download a zipped file', fakeAsync(() => {
        const httpResponse = createBlobHttpResponse();
        const exportReposStub = jest.spyOn(repoExportService, 'exportReposByParticipations').mockReturnValue(of(httpResponse));
        fixture.detectChanges();

        comp.exportRepos();
        tick();
        expect(comp.repositoryExportOptions.addParticipantName).toBeFalse();
        expect(comp.repositoryExportOptions.anonymizeRepository).toBeTrue();
        expect(comp.exportInProgress).toBeFalse();
        expect(exportReposStub).toHaveBeenCalledOnce();
    }));

    it('Export a repo by participant identifiers should download a zipped file', fakeAsync(() => {
        comp.participationIdList = [];
        comp.participantIdentifierList = 'ALL';
        const httpResponse = createBlobHttpResponse();
        const exportReposStub = jest.spyOn(repoExportService, 'exportReposByParticipantIdentifiers').mockReturnValue(of(httpResponse));
        fixture.detectChanges();

        comp.exportRepos();
        tick();
        expect(comp.repositoryExportOptions.addParticipantName).toBeFalse();
        expect(comp.exportInProgress).toBeFalse();
        expect(exportReposStub).toHaveBeenCalledOnce();
    }));

    it('Should not change the ExportOptions during export', fakeAsync(() => {
        comp.participationIdList = [];
        comp.participantIdentifierList = 'ab12cde, cd34efg';
        comp.ngOnInit();

        const copyOfExportOptions = Object.assign({}, comp.repositoryExportOptions);

        const httpResponse = createBlobHttpResponse();
        const exportReposStub = jest.spyOn(repoExportService, 'exportReposByParticipantIdentifiers').mockReturnValue(of(httpResponse));
        fixture.detectChanges();

        comp.exportRepos();
        tick();
        expect(comp.repositoryExportOptions).toEqual(copyOfExportOptions);
        expect(exportReposStub).toHaveBeenCalledOnce();
    }));

    it('Export of multiple repos download multiple files', fakeAsync(() => {
        const programmingExercise2 = new ProgrammingExercise(new Course(), undefined);
        programmingExercise2.id = 43;
        programmingExercise2.isAtLeastInstructor = true;
        comp.programmingExercises = [programmingExercise, programmingExercise2];
        const httpResponse = createBlobHttpResponse();
        const exportReposStub = jest.spyOn(repoExportService, 'exportReposByParticipations').mockReturnValue(of(httpResponse));
        fixture.detectChanges();

        comp.exportRepos();
        tick();
        expect(comp.exportInProgress).toBeFalse();
        expect(exportReposStub).toHaveBeenCalledTimes(2);
    }));

    describe('Export a repo with excludePracticeSubmissions as true', () => {
        const httpResponse = createBlobHttpResponse();

        beforeEach(() => {
            fixture.detectChanges();
            comp.repositoryExportOptions.excludePracticeSubmissions = true;
        });

        it('should call exportReposByParticipations with correct options', fakeAsync(() => {
            comp.participationIdList = participationIdList;
            const exportReposStub = jest.spyOn(repoExportService, 'exportReposByParticipations').mockReturnValue(of(httpResponse));
            comp.exportRepos();
            tick();
            expect(exportReposStub).toHaveBeenCalledExactlyOnceWith(exerciseId, participationIdList, comp.repositoryExportOptions);
        }));

        it('should call exportReposByParticipantIdentifiers with correct options', fakeAsync(() => {
            comp.participationIdList = [];
            comp.participantIdentifierList = 'ALL';
            const exportReposStub = jest.spyOn(repoExportService, 'exportReposByParticipantIdentifiers').mockReturnValue(of(httpResponse));
            comp.exportRepos();
            tick();
            expect(exportReposStub).toHaveBeenCalledExactlyOnceWith(exerciseId, ['ALL'], comp.repositoryExportOptions);
        }));
    });
});

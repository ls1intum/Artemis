import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/programming/manage/assess/repo-export/export-dialog/programming-assessment-repo-export-dialog.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ProgrammingAssessmentRepoExportService } from 'app/programming/manage/assess/repo-export/programming-assessment-repo-export.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { MockProvider } from 'ng-mocks';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
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
    let dialogRef: DynamicDialogRef;

    vi.spyOn(global.URL, 'createObjectURL').mockImplementation(() => 'http://some.test.com');
    vi.spyOn(global.URL, 'revokeObjectURL').mockImplementation(() => undefined);

    const exerciseId = 42;
    const participationIdList = [1];
    const singleParticipantMode = false;
    const programmingExercise = new ProgrammingExercise(new Course(), undefined);
    programmingExercise.id = exerciseId;
    programmingExercise.releaseDate = dayjs();
    programmingExercise.dueDate = dayjs().add(7, 'days');

    beforeEach(() => {
        const mockDialogRef = {
            close: vi.fn(),
        };
        const dialogConfigData = {
            programmingExercises: [programmingExercise],
            participationIdList,
            singleParticipantMode,
        };

        return TestBed.configureTestingModule({
            imports: [FormDateTimePickerComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                LocalStorageService,
                { provide: DynamicDialogRef, useValue: mockDialogRef },
                { provide: DynamicDialogConfig, useValue: { data: dialogConfigData } },
                MockProvider(ExerciseService),
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
                dialogRef = TestBed.inject(DynamicDialogRef);

                // stubs
                vi.spyOn(exerciseService, 'find').mockReturnValue(of({ body: programmingExercise } as HttpResponse<Exercise>));
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('test initialization', () => {
        fixture.detectChanges();
        expect(comp.programmingExercises[0].id).toBe(42);
    });

    it('Exercise service should find the correct programming exercise', () => {
        fixture.detectChanges();
        expect(comp.programmingExercises[0]).toEqual(programmingExercise);
    });

    it('Export a repo by participations should download a zipped file', async () => {
        const httpResponse = createBlobHttpResponse();
        const exportReposStub = vi.spyOn(repoExportService, 'exportReposByParticipations').mockReturnValue(of(httpResponse));
        fixture.detectChanges();

        comp.exportRepos();
        await fixture.whenStable();
        expect(comp.repositoryExportOptions.addParticipantName).toBe(false);
        expect(comp.repositoryExportOptions.anonymizeRepository).toBe(true);
        expect(comp.exportInProgress()).toBe(false);
        expect(exportReposStub).toHaveBeenCalledOnce();
        expect(dialogRef.close).toHaveBeenCalledWith(true);
    });

    it('Export a repo by participant identifiers should download a zipped file', async () => {
        comp.participationIdList = [];
        comp.participantIdentifierList = 'ALL';
        const httpResponse = createBlobHttpResponse();
        const exportReposStub = vi.spyOn(repoExportService, 'exportReposByParticipantIdentifiers').mockReturnValue(of(httpResponse));
        fixture.detectChanges();

        comp.exportRepos();
        await fixture.whenStable();
        expect(comp.repositoryExportOptions.addParticipantName).toBe(false);
        expect(comp.exportInProgress()).toBe(false);
        expect(exportReposStub).toHaveBeenCalledOnce();
    });

    // Reached from the exercise scores page, where the dialog opens with no preselected participations: exporting
    // then sent an empty participant list and the server answered 404. Nothing is selected, so there is nothing to
    // request in the first place.
    it('should not send a request when neither participations nor participants are selected', async () => {
        comp.participationIdList = [];
        comp.participantIdentifierList = '';
        fixture.detectChanges();
        comp.repositoryExportOptions.exportAllParticipants = false;
        const byParticipations = vi.spyOn(repoExportService, 'exportReposByParticipations');
        const byIdentifiers = vi.spyOn(repoExportService, 'exportReposByParticipantIdentifiers');

        comp.exportRepos();
        await fixture.whenStable();

        expect(byParticipations).not.toHaveBeenCalled();
        expect(byIdentifiers).not.toHaveBeenCalled();
        expect(comp.exportInProgress()).toBe(false);
    });

    // A list of only separators and blanks is the same as an empty selection and must not reach the server either.
    it('should not send a request when the participant list holds only separators', async () => {
        comp.participationIdList = [];
        comp.participantIdentifierList = ' , ,, ';
        fixture.detectChanges();
        comp.repositoryExportOptions.exportAllParticipants = false;
        const byIdentifiers = vi.spyOn(repoExportService, 'exportReposByParticipantIdentifiers');

        comp.exportRepos();
        await fixture.whenStable();

        expect(byIdentifiers).not.toHaveBeenCalled();
    });

    // Blank entries around a real login must be dropped rather than sent as empty path segments.
    it('should drop blank entries from the participant list', async () => {
        comp.participationIdList = [];
        comp.participantIdentifierList = ' ab12cde, ,cd34efg ';
        const httpResponse = createBlobHttpResponse();
        const byIdentifiers = vi.spyOn(repoExportService, 'exportReposByParticipantIdentifiers').mockReturnValue(of(httpResponse));
        fixture.detectChanges();
        comp.repositoryExportOptions.exportAllParticipants = false;

        comp.exportRepos();
        await fixture.whenStable();

        expect(byIdentifiers).toHaveBeenCalledWith(exerciseId, ['ab12cde', 'cd34efg'], comp.repositoryExportOptions);
    });

    // Same `[]` is truthy trap as the disable guard: the textarea was hidden behind `!participationIdList`, so the
    // dialog opened from the exercise scores page - which preselects nothing - offered no way to name anyone at all.
    it('should offer the participant field when no participation is preselected', async () => {
        comp.participationIdList = [];
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.nativeElement.querySelector('textarea')).not.toBeNull();
    });

    // With participations preselected the identifiers are irrelevant, so the field stays away.
    it('should hide the participant field when participations are preselected', async () => {
        comp.participationIdList = [1];
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.nativeElement.querySelector('textarea')).toBeNull();
    });

    // The previous guard read `!this.participationIdList`, which is never true because the list starts as an empty
    // array, so the button was never disabled and the tail `&& !this.exportInProgress` even inverted the intent.
    // Nothing being selected deliberately leaves the button enabled - exportRepos() explains that - but a request in
    // flight has to lock it, and only reading a signal makes that reach the zoneless template.
    it('should disable the export button only while an export is running', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        const exportButton = fixture.nativeElement.querySelector('button[type="submit"]');

        expect(exportButton.getAttribute('disabled')).toBeNull();

        comp.exportInProgress.set(true);
        fixture.detectChanges();

        expect(exportButton.getAttribute('disabled')).toBe('');
    });

    it('Should not change the ExportOptions during export', async () => {
        comp.participationIdList = [];
        comp.participantIdentifierList = 'ab12cde, cd34efg';
        comp.ngOnInit();

        const copyOfExportOptions = Object.assign({}, comp.repositoryExportOptions);

        const httpResponse = createBlobHttpResponse();
        const exportReposStub = vi.spyOn(repoExportService, 'exportReposByParticipantIdentifiers').mockReturnValue(of(httpResponse));
        fixture.detectChanges();

        comp.exportRepos();
        await fixture.whenStable();
        expect(comp.repositoryExportOptions).toEqual(copyOfExportOptions);
        expect(exportReposStub).toHaveBeenCalledOnce();
    });

    it('Export of multiple repos download multiple files', async () => {
        const programmingExercise2 = new ProgrammingExercise(new Course(), undefined);
        programmingExercise2.id = 43;
        programmingExercise2.isAtLeastInstructor = true;
        comp.programmingExercises = [programmingExercise, programmingExercise2];
        const httpResponse = createBlobHttpResponse();
        const exportReposStub = vi.spyOn(repoExportService, 'exportReposByParticipations').mockReturnValue(of(httpResponse));
        fixture.detectChanges();

        comp.exportRepos();
        await fixture.whenStable();
        expect(comp.exportInProgress()).toBe(false);
        expect(exportReposStub).toHaveBeenCalledTimes(2);
    });

    it('should close the dialog when cleared', () => {
        fixture.detectChanges();
        comp.clear();
        expect(dialogRef.close).toHaveBeenCalled();
    });

    describe('Export a repo with excludePracticeSubmissions as true', () => {
        const httpResponse = createBlobHttpResponse();

        beforeEach(() => {
            fixture.detectChanges();
            comp.repositoryExportOptions.excludePracticeSubmissions = true;
        });

        it('should call exportReposByParticipations with correct options', async () => {
            comp.participationIdList = participationIdList;
            const exportReposStub = vi.spyOn(repoExportService, 'exportReposByParticipations').mockReturnValue(of(httpResponse));
            comp.exportRepos();
            await fixture.whenStable();
            expect(exportReposStub).toHaveBeenCalledExactlyOnceWith(exerciseId, participationIdList, comp.repositoryExportOptions);
        });

        it('should call exportReposByParticipantIdentifiers with correct options', async () => {
            comp.participationIdList = [];
            comp.participantIdentifierList = 'ALL';
            const exportReposStub = vi.spyOn(repoExportService, 'exportReposByParticipantIdentifiers').mockReturnValue(of(httpResponse));
            comp.exportRepos();
            await fixture.whenStable();
            expect(exportReposStub).toHaveBeenCalledExactlyOnceWith(exerciseId, ['ALL'], comp.repositoryExportOptions);
        });
    });
});

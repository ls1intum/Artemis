import { ComponentFixture, TestBed, tick, fakeAsync } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonStub, stub } from 'sinon';
import { of } from 'rxjs';
import { HttpResponse, HttpHeaders } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import * as moment from 'moment';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-dialog.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ProgrammingAssessmentRepoExportService } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { Exercise } from 'app/entities/exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingAssessmentRepoExportDialogComponent', () => {
    let comp: ProgrammingAssessmentRepoExportDialogComponent;
    let fixture: ComponentFixture<ProgrammingAssessmentRepoExportDialogComponent>;
    let exerciseService: ExerciseService;
    let repoExportService: ProgrammingAssessmentRepoExportService;

    // stubs
    let findExerciseId: SinonStub;
    global.URL.createObjectURL = jest.fn(() => 'http://some.test.com');
    global.URL.revokeObjectURL = jest.fn(() => '');

    const exerciseId = 42;
    const participationIdList = [1];
    const singleParticipantMode = false;
    const programmingExercise = new ProgrammingExercise(new Course(), undefined);
    programmingExercise.id = exerciseId;
    programmingExercise.releaseDate = moment();
    programmingExercise.dueDate = moment().add(7, 'days');

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, FormDateTimePickerModule, ArtemisProgrammingAssessmentModule],
            providers: [
                ExerciseService,
                ProgrammingAssessmentRepoExportService,
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
                findExerciseId = stub(exerciseService, 'find').returns(of({ body: programmingExercise } as HttpResponse<Exercise>));

                comp.exerciseId = exerciseId;
                comp.participationIdList = participationIdList;
                comp.singleParticipantMode = singleParticipantMode;
            });
    });

    it('test initialization', () => {
        fixture.detectChanges();
        expect(comp.exerciseId).to.be.equal(42);
    });

    it('Exerciseservice should find the correct programming exercise', fakeAsync(() => {
        fixture.detectChanges();
        expect(comp.exercise).to.be.equal(programmingExercise);
    }));

    it('Export a repo by participations should download a zipped file', fakeAsync(() => {
        const blob = new Blob([JSON.stringify({ property: 'blob' })], { type: 'application/json' });
        const headers = new HttpHeaders().set('filename', 'blobfile');
        const httpResponse = new HttpResponse({ body: blob, headers });
        const exportReposStub = stub(repoExportService, 'exportReposByParticipations').returns(of(httpResponse));
        fixture.detectChanges();
        comp.exportRepos(exerciseId);
        tick();
        expect(comp.repositoryExportOptions.addParticipantName).to.be.false;
        expect(comp.repositoryExportOptions.hideStudentNameInZippedFolder).to.be.true;
        expect(comp.exportInProgress).to.be.false;
        expect(exportReposStub).to.be.calledOnce;
    }));

    it('Export a repo by participant identifiers should download a zipped file', fakeAsync(() => {
        comp.participationIdList = [];
        comp.participantIdentifierList = 'ALL';
        const blob = new Blob([JSON.stringify({ property: 'blob' })], { type: 'application/json' });
        const headers = new HttpHeaders().set('filename', 'blobfile');
        const httpResponse = new HttpResponse({ body: blob, headers });
        const exportReposStub = stub(repoExportService, 'exportReposByParticipantIdentifiers').returns(of(httpResponse));
        fixture.detectChanges();
        comp.exportRepos(exerciseId);
        tick();
        expect(comp.repositoryExportOptions.addParticipantName).to.be.true;
        expect(comp.exportInProgress).to.be.false;
        expect(exportReposStub).to.be.calledOnce;
    }));

    it('Export of multiple repos download multiple files', fakeAsync(() => {
        const programmingExercise2 = new ProgrammingExercise(new Course(), undefined);
        programmingExercise2.id = 43;
        comp.selectedProgrammingExercises = [programmingExercise, programmingExercise2];

        const blob = new Blob([JSON.stringify({ property: 'blob' })], { type: 'application/json' });
        const headers = new HttpHeaders().set('filename', 'blobfile');
        const httpResponse = new HttpResponse({ body: blob, headers });
        const exportReposStub = stub(repoExportService, 'exportReposByParticipations').returns(of(httpResponse));
        fixture.detectChanges();
        comp.bulkExportRepos();
        expect(comp.repositoryExportOptions.exportAllParticipants).to.be.true;
        tick();
        expect(comp.exportInProgress).to.be.false;
        expect(exportReposStub).to.be.calledTwice;
    }));
});

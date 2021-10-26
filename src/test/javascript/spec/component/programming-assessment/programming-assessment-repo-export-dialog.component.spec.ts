import { ComponentFixture, TestBed, tick, fakeAsync } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { stub } from 'sinon';
import { of } from 'rxjs';
import { HttpResponse, HttpHeaders } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import dayjs from 'dayjs';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-dialog.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ProgrammingAssessmentRepoExportService } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export.service';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { Exercise } from 'app/entities/exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';

chai.use(sinonChai);
const expect = chai.expect;

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

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisProgrammingAssessmentModule],
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
                stub(exerciseService, 'find').returns(of({ body: programmingExercise } as HttpResponse<Exercise>));

                comp.exerciseId = exerciseId;
                comp.participationIdList = participationIdList;
                comp.singleParticipantMode = singleParticipantMode;
            });
    });

    it('test initialization', () => {
        fixture.detectChanges();
        expect(comp.exerciseId).to.be.equal(42);
    });

    it('Exercise service should find the correct programming exercise', () => {
        fixture.detectChanges();
        expect(comp.exercise).to.be.equal(programmingExercise);
    });

    it('Export a repo by participations should download a zipped file', fakeAsync(() => {
        const httpResponse = createBlobHttpResponse();
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
        const httpResponse = createBlobHttpResponse();
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
        fixture.componentInstance.exercise = programmingExercise2;
        fixture.componentInstance.exercise.isAtLeastInstructor = true;
        comp.selectedProgrammingExercises = [programmingExercise, programmingExercise2];
        const httpResponse = createBlobHttpResponse();
        const exportReposStub = stub(repoExportService, 'exportReposByParticipations').returns(of(httpResponse));
        fixture.detectChanges();

        comp.bulkExportRepos();
        tick();
        expect(comp.repositoryExportOptions.exportAllParticipants).to.be.true;
        expect(comp.exportInProgress).to.be.false;
        expect(exportReposStub).to.be.calledTwice;
    }));
});

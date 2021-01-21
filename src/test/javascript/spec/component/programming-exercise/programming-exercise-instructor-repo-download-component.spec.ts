import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-repo-download.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent } from 'ng-mocks';
import * as sinonChai from 'sinon-chai';
import { ProgrammingExerciseInstructorRepositoryType, ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import * as sinon from 'sinon';
import * as chai from 'chai';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseInstructorRepoDownloadComponent', () => {
    let component: ProgrammingExerciseInstructorRepoDownloadComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorRepoDownloadComponent>;
    let service: ProgrammingExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot()],
            declarations: [ProgrammingExerciseInstructorRepoDownloadComponent, MockComponent(ButtonComponent)],
            providers: [{ provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseInstructorRepoDownloadComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(ProgrammingExerciseService);
    });

    afterEach(function () {
        // completely restore all fakes created through the sandbox
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });

    it('should not download when there is no exercise', () => {
        const spy = sinon.spy(service, 'exportInstructorRepository');
        component.exportRepository();
        expect(spy).callCount(0);
    });

    it('should download the repos', () => {
        const spy = sinon.spy(service, 'exportInstructorRepository');

        component.exerciseId = 1;
        const repoTypes: ProgrammingExerciseInstructorRepositoryType[] = ['tests', 'solution', 'tests'];
        repoTypes.forEach((repoType) => {
            component.repositoryType = repoType;
            component.exportRepository();
            expect(spy).callCount(1);
            spy.resetHistory();
        });
    });
});

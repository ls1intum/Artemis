import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProgrammingExerciseInstructorExerciseDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-exercise-download.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent } from 'ng-mocks';
import sinonChai from 'sinon-chai';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import * as sinon from 'sinon';
import * as chai from 'chai';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseInstructorExerciseDownloadComponent', () => {
    let component: ProgrammingExerciseInstructorExerciseDownloadComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorExerciseDownloadComponent>;
    let service: ProgrammingExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot()],
            declarations: [ProgrammingExerciseInstructorExerciseDownloadComponent, MockComponent(ButtonComponent)],
            providers: [{ provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseInstructorExerciseDownloadComponent);
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
        const spy = sinon.spy(service, 'exportInstructorExercise');
        component.exportExercise();
        expect(spy).callCount(0);
        spy.resetHistory();
    });

    it('should download the exercise', () => {
        const spy = sinon.spy(service, 'exportInstructorExercise');
        component.exerciseId = 1;
        component.exportExercise();
        expect(spy).callCount(1);
        spy.resetHistory();
    });
});

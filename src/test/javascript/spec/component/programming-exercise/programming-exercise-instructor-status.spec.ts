import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { AceEditorModule } from 'ng2-ace-editor';
import * as chai from 'chai';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/entities/programming-exercise';
import { ParticipationType } from 'app/entities/programming-exercise';
import { ArTEMiSTestModule } from '../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

const expect = chai.expect;

describe('ProgrammingExerciseInstructorStatusComponent', () => {
    let comp: ProgrammingExerciseInstructorStatusComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorStatusComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, AceEditorModule, NgbModule],
            declarations: [ProgrammingExerciseInstructorStatusComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructorStatusComponent);
                comp = fixture.componentInstance;
            });
    }));

    it('should not show anything without inputs', () => {
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).to.not.exist;
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).to.not.exist;
    });

    it('should not show anything if participationType is Assignment', () => {
        comp.participationType = ParticipationType.ASSIGNMENT;
        comp.result = { id: 1, successful: true, score: 100 };
        fixture.detectChanges();
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).to.not.exist;
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).to.not.exist;
    });

    [ParticipationType.TEMPLATE, ParticipationType.SOLUTION].map(participationType =>
        it('should not show anything if there is no result', () => {
            comp.participationType = participationType;
            comp.result = null;
            fixture.detectChanges();
            const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
            expect(templateStatus).to.not.exist;
            const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
            expect(solutionStatus).to.not.exist;
        }),
    );

    it('should show nothing if the participation is template and the result has a score of 0', () => {
        comp.participationType = ParticipationType.TEMPLATE;
        comp.result = { id: 1, score: 0 };
        fixture.detectChanges();
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).to.not.exist;
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).to.not.exist;
    });

    it('should show nothing if the participation is solution and the result is successful', () => {
        comp.participationType = ParticipationType.SOLUTION;
        comp.result = { id: 1, successful: true };
        fixture.detectChanges();
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).to.not.exist;
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).to.not.exist;
    });

    it('should show a template warning if the participation is template and the score is > 0', () => {
        comp.participationType = ParticipationType.TEMPLATE;
        comp.result = { id: 1, score: 1 };
        fixture.detectChanges();
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).to.exist;
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).to.not.exist;
    });

    it('should show a solution warning if the participation is solution and the result is not successful', () => {
        comp.participationType = ParticipationType.SOLUTION;
        comp.result = { id: 1, successful: false };
        fixture.detectChanges();
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).to.not.exist;
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).to.exist;
    });
});

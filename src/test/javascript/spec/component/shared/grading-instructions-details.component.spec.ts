import { GradingInstructionsDetailsComponent } from 'app/exercises/shared/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { Exercise } from 'app/entities/exercise.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';

describe('Grading Instructions Management Component', () => {
    let comp: GradingInstructionsDetailsComponent;
    let fixture: ComponentFixture<GradingInstructionsDetailsComponent>;
    const gradingInstruction = { id: 1, credits: 1, gradingScale: 'scale', instructionDescription: 'description', feedback: 'feedback', usageCount: 0 } as GradingInstruction;
    const gradingCriterion = { id: 1, title: 'testCriteria', structuredGradingInstructions: [gradingInstruction] } as GradingCriterion;
    const exercise = { id: 1 } as Exercise;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [GradingInstructionsDetailsComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(GradingInstructionsDetailsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(GradingInstructionsDetailsComponent);
        comp = fixture.componentInstance;
        comp.exercise = exercise;
    });

    describe('OnInit', function () {
        it('should Set the default grading criteria', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async

            // THEN
            expect(comp.questionEditorText).toEqual(
                '[gradingInstruction]\n' +
                    '\t[credits]  0\n' +
                    '\t[gradingScale]  Add instruction grading scale here (only visible for tutors)\n' +
                    '\t[description]  Add grading instruction here (only visible for tutors)\n' +
                    '\t[feedback]  Add feedback for students here (visible for students)\n' +
                    '\t[maxCountInScore]  0\n' +
                    '\n' +
                    '[gradingCriterion]This is an Example criterion\n' +
                    '\t[gradingInstruction]\n' +
                    '\t[credits]  0\n' +
                    '\t[gradingScale]  Add instruction grading scale here (only visible for tutors)\n' +
                    '\t[description]  Add grading instruction here (only visible for tutors)\n' +
                    '\t[feedback]  Add feedback for students here (visible for students)\n' +
                    '\t[maxCountInScore]  0\n' +
                    '\n' +
                    '[gradingInstruction]\n' +
                    '\t[credits] 0\n' +
                    '\t[gradingScale]  Add instruction grading scale here (only visible for tutors)\n' +
                    '\t[description]  Add grading instruction here (only visible for tutors)\n' +
                    '\t[feedback]  Add feedback for students here (visible for students)\n' +
                    '\t[maxCountInScore] 0\n' +
                    '\n',
            );
        }));
        it('should set the grading criteria based on the exercise', fakeAsync(() => {
            comp.exercise.gradingCriteria = [gradingCriterion];
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.questionEditorText).toEqual(
                '[gradingCriterion]' +
                    'testCriteria' +
                    '\n' +
                    '\t' +
                    '[gradingInstruction]\n' +
                    '\t' +
                    '[credits]' +
                    ' 1\n' +
                    '\t' +
                    '[gradingScale] scale\n' +
                    '\t' +
                    '[description] description\n' +
                    '\t' +
                    '[feedback] feedback\n' +
                    '\t' +
                    '[maxCountInScore] 0\n\n',
            );
        }));
    });
});

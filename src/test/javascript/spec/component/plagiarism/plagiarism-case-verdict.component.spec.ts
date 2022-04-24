import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PlagiarismCaseVerdictComponent } from 'app/course/plagiarism-cases/shared/verdict/plagiarism-case-verdict.component';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';

describe('Plagiarism Case Verdict Component', () => {
    let comp: PlagiarismCaseVerdictComponent;
    let fixture: ComponentFixture<PlagiarismCaseVerdictComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule],
            declarations: [PlagiarismCaseVerdictComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismCaseVerdictComponent);
        comp = fixture.componentInstance;
    });

    it('should return correct translation string', () => {
        comp.plagiarismCase = {} as PlagiarismCase;
        expect(comp.verdictTranslationString).toBe('artemisApp.plagiarism.plagiarismCases.verdict.none');
        comp.plagiarismCase = {
            verdict: PlagiarismVerdict.PLAGIARISM,
        } as PlagiarismCase;
        expect(comp.verdictTranslationString).toBe('artemisApp.plagiarism.plagiarismCases.verdict.plagiarism');
        comp.plagiarismCase.verdict = PlagiarismVerdict.POINT_DEDUCTION;
        expect(comp.verdictTranslationString).toBe('artemisApp.plagiarism.plagiarismCases.verdict.pointDeduction');
        comp.plagiarismCase.verdict = PlagiarismVerdict.WARNING;
        expect(comp.verdictTranslationString).toBe('artemisApp.plagiarism.plagiarismCases.verdict.warning');
    });

    it('should return correct verdict badge class', () => {
        comp.plagiarismCase = {} as PlagiarismCase;
        expect(comp.verdictBadgeClass).toEqual(['bg-secondary']);
        comp.plagiarismCase = {
            verdict: PlagiarismVerdict.PLAGIARISM,
        } as PlagiarismCase;
        expect(comp.verdictBadgeClass).toEqual(['bg-primary']);
    });
});

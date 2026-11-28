import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PlagiarismCaseVerdictComponent } from 'app/plagiarism/shared/verdict/plagiarism-case-verdict.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { By } from '@angular/platform-browser';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { MockPipe } from 'ng-mocks';

describe('Plagiarism Case Verdict Component', () => {
    let comp: PlagiarismCaseVerdictComponent;
    let fixture: ComponentFixture<PlagiarismCaseVerdictComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [PlagiarismCaseVerdictComponent, MockPipe(ArtemisDatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismCaseVerdictComponent);
        comp = fixture.componentInstance;
    });

    it.each([
        [undefined, 'artemisApp.plagiarism.plagiarismCases.verdict.none'],
        [PlagiarismVerdict.PLAGIARISM, 'artemisApp.plagiarism.plagiarismCases.verdict.plagiarism'],
        [PlagiarismVerdict.WARNING, 'artemisApp.plagiarism.plagiarismCases.verdict.warning'],
        [PlagiarismVerdict.POINT_DEDUCTION, 'artemisApp.plagiarism.plagiarismCases.verdict.pointDeduction'],
        [PlagiarismVerdict.NO_PLAGIARISM, 'artemisApp.plagiarism.plagiarismCases.verdict.noPlagiarism'],
    ])('should return correct translation string', (verdict: PlagiarismVerdict | undefined, message: string) => {
        fixture.componentRef.setInput('plagiarismCase', {
            verdict,
        } as PlagiarismCase);
        fixture.detectChanges();

        expect(comp.verdictTranslationString).toBe(message);
    });

    it.each([
        [undefined, 'secondary'],
        [PlagiarismVerdict.PLAGIARISM, 'danger'],
        [PlagiarismVerdict.WARNING, 'danger'],
        [PlagiarismVerdict.POINT_DEDUCTION, 'danger'],
        [PlagiarismVerdict.NO_PLAGIARISM, 'success'],
    ])('should render the verdict badge with the matching severity', (verdict: PlagiarismVerdict | undefined, severity: string) => {
        fixture.componentRef.setInput('plagiarismCase', {
            verdict,
        } as PlagiarismCase);

        fixture.detectChanges();

        const badge = fixture.debugElement.query(By.css('[data-testid="plagiarism-case-verdict-badge"]'));
        expect(badge.nativeElement.querySelector('[data-severity]').getAttribute('data-severity')).toBe(severity);
    });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PlagiarismCaseVerdictComponent } from 'app/course/plagiarism-cases/shared/verdict/plagiarism-case-verdict.component';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { By } from '@angular/platform-browser';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockPipe } from 'ng-mocks';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

describe('Plagiarism Case Verdict Component', () => {
    let comp: PlagiarismCaseVerdictComponent;
    let fixture: ComponentFixture<PlagiarismCaseVerdictComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule, NgbModule],
            declarations: [PlagiarismCaseVerdictComponent, MockPipe(ArtemisDatePipe)],
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
        comp.plagiarismCase = { verdict } as PlagiarismCase;
        fixture.detectChanges();

        expect(comp.verdictTranslationString).toBe(message);
    });

    it.each([
        [undefined, 'bg-secondary'],
        [PlagiarismVerdict.PLAGIARISM, 'bg-danger'],
        [PlagiarismVerdict.WARNING, 'bg-danger'],
        [PlagiarismVerdict.POINT_DEDUCTION, 'bg-danger'],
        [PlagiarismVerdict.NO_PLAGIARISM, 'bg-success'],
    ])('should return correct verdict badge class', (verdict: PlagiarismVerdict | undefined, className: string) => {
        comp.plagiarismCase = { verdict } as PlagiarismCase;
        fixture.detectChanges();

        const element = fixture.debugElement.query(By.css('.badge'));
        expect(element.classes).toHaveProperty(className);
    });
});

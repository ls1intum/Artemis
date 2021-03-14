import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { PlagiarismHeaderComponent } from 'app/exercises/shared/plagiarism/plagiarism-header/plagiarism-header.component';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';

describe('Plagiarism Header Component', () => {
    let comp: PlagiarismHeaderComponent;
    let fixture: ComponentFixture<PlagiarismHeaderComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule],
            declarations: [PlagiarismHeaderComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismHeaderComponent);
        comp = fixture.componentInstance;

        comp.comparison = {
            submissionA: { studentLogin: 'studentA' },
            submissionB: { studentLogin: 'studentB' },
        } as PlagiarismComparison<ModelingSubmissionElement>;
        comp.splitControlSubject = new Subject<string>();
    });

    it('should confirm a plagiarism', () => {
        spyOn(comp, 'updatePlagiarismStatus');
        comp.confirmPlagiarism();

        expect(comp.updatePlagiarismStatus).toHaveBeenCalledWith(PlagiarismStatus.CONFIRMED);
    });

    it('should deny a plagiarism', () => {
        spyOn(comp, 'updatePlagiarismStatus');
        comp.denyPlagiarism();

        expect(comp.updatePlagiarismStatus).toHaveBeenCalledWith(PlagiarismStatus.DENIED);
    });

    it('should emit when expanding left split view pane', () => {
        comp.splitControlSubject = new Subject<string>();
        spyOn(comp.splitControlSubject, 'next');

        const nativeElement = fixture.nativeElement;
        const splitLeftButton = nativeElement.querySelector("[data-qa='split-view-left']");
        splitLeftButton.dispatchEvent(new Event('click'));

        fixture.detectChanges();

        expect(comp.splitControlSubject.next).toHaveBeenCalledWith('left');
    });

    it('should emit when expanding right split view pane', () => {
        comp.splitControlSubject = new Subject<string>();
        spyOn(comp.splitControlSubject, 'next');

        const nativeElement = fixture.nativeElement;
        const splitRightButton = nativeElement.querySelector("[data-qa='split-view-right']");
        splitRightButton.dispatchEvent(new Event('click'));

        fixture.detectChanges();

        expect(comp.splitControlSubject.next).toHaveBeenCalledWith('right');
    });

    it('should emit when resetting the split panes', () => {
        comp.splitControlSubject = new Subject<string>();
        spyOn(comp.splitControlSubject, 'next');

        const nativeElement = fixture.nativeElement;
        const splitHalfButton = nativeElement.querySelector("[data-qa='split-view-even']");
        splitHalfButton.dispatchEvent(new Event('click'));

        fixture.detectChanges();

        expect(comp.splitControlSubject.next).toHaveBeenCalledWith('even');
    });
});

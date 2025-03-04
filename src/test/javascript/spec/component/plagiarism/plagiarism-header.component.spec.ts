import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Observable, of, Subject } from 'rxjs';
import { PlagiarismHeaderComponent } from 'app/exercises/shared/plagiarism/plagiarism-header/plagiarism-header.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { Exercise } from 'app/entities/exercise.model';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('Plagiarism Header Component', () => {
    let comp: PlagiarismHeaderComponent;
    let fixture: ComponentFixture<PlagiarismHeaderComponent>;
    let plagiarismCasesService: PlagiarismCasesService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [PlagiarismHeaderComponent, MockDirective(TranslateDirective)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismHeaderComponent);
        comp = fixture.componentInstance;

        plagiarismCasesService = TestBed.inject(PlagiarismCasesService);
        comp.comparison = {
            submissionA: { studentLogin: 'studentA' },
            submissionB: { studentLogin: 'studentB' },
            status: PlagiarismStatus.NONE,
        } as PlagiarismComparison<ModelingSubmissionElement>;
        comp.exercise = { course: { id: 1 } } as Exercise;
        comp.splitControlSubject = new Subject<string>();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should confirm a plagiarism', () => {
        jest.spyOn(comp, 'updatePlagiarismStatus');
        comp.confirmPlagiarism();

        expect(comp.updatePlagiarismStatus).toHaveBeenCalledWith(PlagiarismStatus.CONFIRMED);
        expect(comp.isLoading).toBeTrue();
    });

    it('should deny a plagiarism', () => {
        jest.spyOn(comp, 'updatePlagiarismStatus');
        comp.denyPlagiarism();

        expect(comp.updatePlagiarismStatus).toHaveBeenCalledWith(PlagiarismStatus.DENIED);
        expect(comp.isLoading).toBeTrue();
    });

    it('should disable deny button if plagiarism status is dirty', () => {
        comp.comparison.status = PlagiarismStatus.DENIED;
        comp.isLoading = true;

        const nativeElement = fixture.nativeElement;
        const button = nativeElement.querySelector("[data-qa='deny-plagiarism-button']") as ButtonComponent;
        fixture.detectChanges();

        expect(button.disabled).toBeTrue();
    });

    it('should open a confirmation popup to deny a plagiarism if it is changing from confirmed to denied', () => {
        jest.spyOn(comp, 'updatePlagiarismStatus');
        const modalSpy = jest.spyOn(fixture.debugElement.injector.get(NgbModal), 'open');

        comp.comparison.status = PlagiarismStatus.CONFIRMED;

        comp.denyPlagiarism();

        expect(comp.updatePlagiarismStatus).not.toHaveBeenCalled();
        expect(modalSpy).toHaveBeenCalledOnce();
        expect(comp.isLoading).toBeTrue();
    });

    it('should update the plagiarism status', fakeAsync(() => {
        const updatePlagiarismComparisonStatusStub = jest
            .spyOn(plagiarismCasesService, 'updatePlagiarismComparisonStatus')
            .mockReturnValue(of({}) as Observable<HttpResponse<void>>);
        comp.updatePlagiarismStatus(PlagiarismStatus.CONFIRMED);

        tick();

        expect(updatePlagiarismComparisonStatusStub).toHaveBeenCalledOnce();
        expect(comp.comparison.status).toEqual(PlagiarismStatus.CONFIRMED);
        expect(comp.isLoading).toBeFalse();
    }));

    it('should emit when expanding left split view pane', () => {
        comp.splitControlSubject = new Subject<string>();
        jest.spyOn(comp.splitControlSubject, 'next');

        const nativeElement = fixture.nativeElement;
        const splitLeftButton = nativeElement.querySelector("[data-qa='split-view-left']");
        splitLeftButton.dispatchEvent(new Event('click'));

        fixture.detectChanges();

        expect(comp.splitControlSubject.next).toHaveBeenCalledWith('left');
    });

    it('should emit when expanding right split view pane', () => {
        comp.splitControlSubject = new Subject<string>();
        jest.spyOn(comp.splitControlSubject, 'next');

        const nativeElement = fixture.nativeElement;
        const splitRightButton = nativeElement.querySelector("[data-qa='split-view-right']");
        splitRightButton.dispatchEvent(new Event('click'));

        fixture.detectChanges();

        expect(comp.splitControlSubject.next).toHaveBeenCalledWith('right');
    });

    it('should emit when resetting the split panes', () => {
        comp.splitControlSubject = new Subject<string>();
        jest.spyOn(comp.splitControlSubject, 'next');

        const nativeElement = fixture.nativeElement;
        const splitHalfButton = nativeElement.querySelector("[data-qa='split-view-even']");
        splitHalfButton.dispatchEvent(new Event('click'));

        fixture.detectChanges();

        expect(comp.splitControlSubject.next).toHaveBeenCalledWith('even');
    });

    it.each(['confirm-plagiarism-button', 'deny-plagiarism-button'])('should disable status update button for team exercises', (selector) => {
        comp.exercise.teamMode = true;
        comp.comparison.status = PlagiarismStatus.NONE;
        fixture.detectChanges();

        const nativeElement = fixture.nativeElement;
        const button = nativeElement.querySelector(`[data-qa=${selector}]`) as ButtonComponent;
        fixture.detectChanges();

        expect(button.disabled).toBeTrue();
    });

    it('should display team mode disabled help icon when teamMode is enabled', () => {
        comp.exercise.teamMode = true;
        fixture.detectChanges();

        const nativeElement = fixture.nativeElement;
        const helpIcon = nativeElement.querySelector('jhi-help-icon');

        expect(helpIcon).toBeTruthy();
        expect(helpIcon.getAttribute('text')).toBe('artemisApp.plagiarism.teamModeDisabled');
    });

    it('should hide team mode disabled help icon when teamMode is disabled', () => {
        comp.exercise.teamMode = false;
        fixture.detectChanges();

        const nativeElement = fixture.nativeElement;
        const helpIcon = nativeElement.querySelector('jhi-help-icon');

        expect(helpIcon).toBeFalsy();
    });
});

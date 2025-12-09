import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Observable, Subject, of } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { PlagiarismHeaderComponent } from 'app/plagiarism/manage/plagiarism-header/plagiarism-header.component';
import { AlertService } from '../../../shared/service/alert.service';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';

describe('Plagiarism Header Component', () => {
    let comp: PlagiarismHeaderComponent;
    let fixture: ComponentFixture<PlagiarismHeaderComponent>;
    let plagiarismCasesService: PlagiarismCasesService;
    const alertServiceMock = { error: jest.fn(), addAlert: jest.fn() };

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [PlagiarismHeaderComponent, MockDirective(TranslateDirective)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: NgbModal, useClass: MockNgbModalService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismHeaderComponent);
        comp = fixture.componentInstance;

        plagiarismCasesService = TestBed.inject(PlagiarismCasesService);
        fixture.componentRef.setInput('comparison', {
            id: 1,
            submissionA: { studentLogin: 'studentA' },
            submissionB: { studentLogin: 'studentB' },
            status: PlagiarismStatus.NONE,
        } as PlagiarismComparison);
        fixture.componentRef.setInput('exercise', { course: { id: 1 } } as Exercise);
        fixture.componentRef.setInput('splitControlSubject', new Subject<string>());
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

    it('should disable deny button if plagiarism status is denied or loading', () => {
        fixture.componentRef.setInput('comparison', Object.assign({}, comp.comparison(), { status: PlagiarismStatus.DENIED }));
        comp.isLoading = true;

        fixture.detectChanges();

        const nativeElement = fixture.nativeElement;
        const button = nativeElement.querySelector("[data-qa='deny-plagiarism-button']") as HTMLButtonElement;

        expect(button).toBeTruthy();
        expect(button.disabled).toBeTrue();
    });

    it('should open a confirmation popup to deny a plagiarism if it is changing from confirmed to denied', () => {
        jest.spyOn(comp, 'updatePlagiarismStatus');
        const modalSpy = jest.spyOn(TestBed.inject(NgbModal), 'open');

        fixture.componentRef.setInput('comparison', Object.assign({}, comp.comparison(), { status: PlagiarismStatus.CONFIRMED }));

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
        expect(comp.comparison()?.status).toEqual(PlagiarismStatus.CONFIRMED);
        expect(comp.isLoading).toBeFalse();
    }));

    it('should emit when expanding left split view pane', () => {
        // we set the splitControlSubject in beforeEach, hence we can assume it is defined
        jest.spyOn(comp.splitControlSubject()!, 'next');

        const nativeElement = fixture.nativeElement;
        const splitLeftButton = nativeElement.querySelector("[data-qa='split-view-left']");
        splitLeftButton.dispatchEvent(new Event('click'));

        fixture.detectChanges();

        expect(comp.splitControlSubject()?.next).toHaveBeenCalledWith('left');
    });

    it('should emit when expanding right split view pane', () => {
        // we set the splitControlSubject in beforeEach, hence we can assume it is defined
        jest.spyOn(comp.splitControlSubject()!, 'next');

        const nativeElement = fixture.nativeElement;
        const splitRightButton = nativeElement.querySelector("[data-qa='split-view-right']");
        splitRightButton.dispatchEvent(new Event('click'));

        fixture.detectChanges();

        expect(comp.splitControlSubject()?.next).toHaveBeenCalledWith('right');
    });

    it('should emit when resetting the split panes', () => {
        // we set the splitControlSubject in beforeEach, hence we can assume it is defined
        jest.spyOn(comp.splitControlSubject()!, 'next');

        const nativeElement = fixture.nativeElement;
        const splitHalfButton = nativeElement.querySelector("[data-qa='split-view-even']");
        splitHalfButton.dispatchEvent(new Event('click'));

        fixture.detectChanges();

        expect(comp.splitControlSubject()?.next).toHaveBeenCalledWith('even');
    });

    it('should display team mode disabled help icon when teamMode is enabled', () => {
        fixture.componentRef.setInput('exercise', Object.assign({}, comp.exercise(), { teamMode: true }));
        fixture.detectChanges();

        const nativeElement = fixture.nativeElement;
        const helpIcon = nativeElement.querySelector('fa-icon'); // Update to select FontAwesome icon
        const textElement = nativeElement.querySelector('p small'); // Select the text inside the paragraph

        expect(helpIcon).toBeTruthy(); // The icon should be present
        expect(textElement).toBeTruthy();
        expect(textElement.getAttribute('jhiTranslate')).toBe('artemisApp.plagiarism.teamModeDisabled');
    });

    it('should hide team mode disabled help icon when teamMode is disabled', () => {
        fixture.componentRef.setInput('exercise', Object.assign({}, comp.exercise(), { teamMode: false }));
        fixture.detectChanges();

        const nativeElement = fixture.nativeElement;
        const helpIcon = nativeElement.querySelector('fa-icon');
        const textElement = nativeElement.querySelector('p small');

        expect(helpIcon).toBeFalsy(); // The icon should not be present
        expect(textElement).toBeFalsy(); // The text should not be present
    });

    it('shows an error and aborts when the course id is missing', () => {
        fixture.componentRef.setInput('comparison', { id: 42 } as any);

        fixture.componentRef.setInput('exercise', undefined);
        fixture.detectChanges();
        const alertSpy = jest.spyOn(alertServiceMock, 'error');
        const updateSpy = jest.spyOn(plagiarismCasesService, 'updatePlagiarismComparisonStatus');

        comp.updatePlagiarismStatus(PlagiarismStatus.CONFIRMED);

        expect(alertSpy).toHaveBeenCalledWith('error.courseIdUndefined');
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(updateSpy).not.toHaveBeenCalled();
        expect(comp.isLoading).toBeFalse();
    });
});

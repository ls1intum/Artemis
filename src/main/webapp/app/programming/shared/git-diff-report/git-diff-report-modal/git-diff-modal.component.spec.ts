import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { ArtemisTranslatePipe } from '../../../../shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GitDiffReportModalComponent } from 'app/programming/shared/git-diff-report/git-diff-report-modal/git-diff-report-modal.component';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';

describe('GitDiffReportModalComponent', () => {
    let comp: GitDiffReportModalComponent;
    let fixture: ComponentFixture<GitDiffReportModalComponent>;
    let modal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockComponent(GitDiffReportComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(NgbActiveModal),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffReportModalComponent);
        comp = fixture.componentInstance;
        modal = TestBed.inject(NgbActiveModal);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call modal service when close() is invoked', () => {
        const modalServiceSpy = jest.spyOn(modal, 'dismiss');
        comp.close();
        expect(modalServiceSpy).toHaveBeenCalled();
    });
});

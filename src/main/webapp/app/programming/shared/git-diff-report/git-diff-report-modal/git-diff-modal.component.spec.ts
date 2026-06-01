import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GitDiffReportModalComponent } from 'app/programming/shared/git-diff-report/git-diff-report-modal/git-diff-report-modal.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('GitDiffReportModalComponent', () => {
    setupTestBed({ zoneless: true });
    let comp: GitDiffReportModalComponent;
    let fixture: ComponentFixture<GitDiffReportModalComponent>;
    let dialogRef: DynamicDialogRef;

    beforeEach(() => {
        const mockDialogRef = {
            close: vi.fn(),
        };

        TestBed.configureTestingModule({
            imports: [MockComponent(GitDiffReportComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: DynamicDialogRef, useValue: mockDialogRef },
                { provide: DynamicDialogConfig, useValue: { data: {} } },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffReportModalComponent);
        comp = fixture.componentInstance;
        dialogRef = TestBed.inject(DynamicDialogRef);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should call dialog service when close() is invoked', () => {
        const dialogCloseSpy = vi.spyOn(dialogRef, 'close');
        comp.close();
        expect(dialogCloseSpy).toHaveBeenCalled();
    });
});

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { ArtemisTranslatePipe } from '../../../../shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GitDiffReportModalComponent } from 'app/programming/shared/git-diff-report/git-diff-report-modal/git-diff-report-modal.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';

describe('GitDiffReportModalComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: GitDiffReportModalComponent;
    let fixture: ComponentFixture<GitDiffReportModalComponent>;
    let dialogRef: DynamicDialogRef;

    const dialogRefMock = {
        close: vi.fn(),
    } as unknown as DynamicDialogRef;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockComponent(GitDiffReportComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRefMock },
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

    it('should close the dialog when close() is invoked', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');
        comp.close();
        expect(closeSpy).toHaveBeenCalled();
    });

    it('should hydrate signals from DynamicDialogConfig data on init', () => {
        const dialogConfig = TestBed.inject(DynamicDialogConfig);
        dialogConfig.data = {
            repositoryDiffInformation: { totalLineChange: { addedLineCount: 1, removedLineCount: 2 } },
            diffForTemplateAndSolution: false,
        };
        comp.ngOnInit();
        expect(comp.repositoryDiffInformation()).toEqual(dialogConfig.data.repositoryDiffInformation);
        expect(comp.diffForTemplateAndSolution()).toBeFalsy();
    });
});

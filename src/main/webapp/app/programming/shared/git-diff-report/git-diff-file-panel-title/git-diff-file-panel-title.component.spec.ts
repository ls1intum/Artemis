import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GitDiffFilePanelTitleComponent } from 'app/programming/shared/git-diff-report/git-diff-file-panel-title/git-diff-file-panel-title.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DiffInformation, FileStatus } from 'app/programming/shared/utils/diff.utils';

describe('GitDiffFilePanelTitleComponent', () => {
    let fixture: ComponentFixture<GitDiffFilePanelTitleComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffFilePanelTitleComponent);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each([
        {
            diffInformation: {
                title: 'some-unchanged-file.java',
                modifiedPath: 'some-unchanged-file.java',
                originalPath: 'some-unchanged-file.java',
                fileStatus: FileStatus.UNCHANGED,
                diffReady: false,
            } as DiffInformation,
            title: 'some-unchanged-file.java',
            status: FileStatus.UNCHANGED,
            shouldShowBadge: false,
        },
        {
            diffInformation: {
                title: 'some-deleted-file.java',
                modifiedPath: '',
                originalPath: 'some-deleted-file.java',
                fileStatus: FileStatus.DELETED,
                diffReady: false,
            } as DiffInformation,
            title: 'some-deleted-file.java',
            status: FileStatus.DELETED,
            shouldShowBadge: true,
            badgeClass: 'bg-danger',
        },
        {
            diffInformation: {
                title: 'some-created-file.java',
                modifiedPath: 'some-created-file.java',
                originalPath: '',
                fileStatus: FileStatus.CREATED,
                diffReady: false,
            } as DiffInformation,
            title: 'some-created-file.java',
            status: FileStatus.CREATED,
            shouldShowBadge: true,
            badgeClass: 'bg-success',
        },
        {
            diffInformation: {
                title: 'some-file.java → some-renamed-file.java',
                modifiedPath: 'some-renamed-file.java',
                originalPath: 'some-file.java',
                fileStatus: FileStatus.RENAMED,
                diffReady: false,
            } as DiffInformation,
            title: 'some-file.java → some-renamed-file.java',
            status: FileStatus.RENAMED,
            shouldShowBadge: true,
            badgeClass: 'bg-warning',
        },
    ])('should correctly display title and status badge', ({ diffInformation, title, status, shouldShowBadge, badgeClass }) => {
        fixture.componentRef.setInput('diffInformation', diffInformation);
        fixture.detectChanges();

        const element = fixture.nativeElement;
        const titleElement = element.querySelector('.file-path-with-badge');
        const badge = element.querySelector('.badge');

        if (shouldShowBadge) {
            expect(badge).toBeTruthy();
            expect(badge.classList.contains(badgeClass)).toBeTrue();
            const badgeText = `artemisApp.programmingExercise.diffReport.fileChange.${status.toLowerCase()}`;
            expect(titleElement.textContent.trim()).toBe(`${title} ${badgeText}`);
        } else {
            expect(badge).toBeFalsy();
            expect(titleElement.textContent.trim()).toBe(title);
        }
    });
});

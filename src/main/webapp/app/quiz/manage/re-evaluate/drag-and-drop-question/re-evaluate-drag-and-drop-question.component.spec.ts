import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ReEvaluateDragAndDropQuestionComponent } from 'app/quiz/manage/re-evaluate/drag-and-drop-question/re-evaluate-drag-and-drop-question.component';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'src/test/javascript/spec/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'src/test/javascript/spec/helpers/mocks/service/mock-profile.service';

describe('ReEvaluateDragAndDropQuestionComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ReEvaluateDragAndDropQuestionComponent>;
    let component: ReEvaluateDragAndDropQuestionComponent;

    const fileName1 = 'test1.jpg';
    const file1 = new File([], fileName1);
    const fileName2 = 'test2.jpg';
    const file2 = new File([], fileName2);
    const fileName3 = 'test3.png';
    const file3 = new File([], fileName3);

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ReEvaluateDragAndDropQuestionComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should add file', () => {
        const path = 'this/is/a/path/to/a/file.png';
        component.handleAddFile({ fileName: fileName1, file: file1 });
        component.handleAddFile({ fileName: fileName2, file: file2, path });

        expect(component.fileMap).toEqual(
            new Map<string, { file: File; path?: string }>([
                [fileName1, { file: file1 }],
                [fileName2, { file: file2, path }],
            ]),
        );
    });

    it('should remove file', () => {
        component.fileMap = new Map<string, { file: File; path?: string }>([
            [fileName1, { file: file1 }],
            [fileName2, { file: file2 }],
            [fileName3, { file: file3 }],
        ]);
        component.handleRemoveFile(fileName2);
        expect(component.fileMap).toEqual(
            new Map<string, { file: File; path?: string }>([
                [fileName1, { file: file1 }],
                [fileName3, { file: file3 }],
            ]),
        );
    });
});

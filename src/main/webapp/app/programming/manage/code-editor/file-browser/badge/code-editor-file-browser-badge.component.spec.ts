import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { FileBadge, FileBadgeType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorFileBrowserBadgeComponent } from 'app/programming/manage/code-editor/file-browser/badge/code-editor-file-browser-badge.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CodeEditorFileBrowserBadgeComponent', () => {
    let component: CodeEditorFileBrowserBadgeComponent;
    let fixture: ComponentFixture<CodeEditorFileBrowserBadgeComponent>;
    let translateService: TranslateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });

        translateService = TestBed.inject(TranslateService);

        fixture = TestBed.createComponent(CodeEditorFileBrowserBadgeComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('badge', new FileBadge(FileBadgeType.FEEDBACK_SUGGESTION, 3));
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should correctly display the tooltip for a FEEDBACK_SUGGESTION badge', () => {
        vi.spyOn(translateService, 'instant').mockReturnValue('Mocked Tooltip');
        expect(component.tooltip).toBe('Mocked Tooltip');
    });

    it('should show the count without an icon, like the general feedback count', () => {
        const pill: HTMLElement = fixture.nativeElement.querySelector('.file-browser-badge');
        expect(pill.textContent?.trim()).toBe('3');
        expect(pill.querySelector('fa-icon')).toBeNull();
    });

    it('should correctly display the tooltip for a REVIEW_COMMENT badge', () => {
        fixture.componentRef.setInput('badge', new FileBadge(FileBadgeType.REVIEW_COMMENT, 2));
        vi.spyOn(translateService, 'instant').mockReturnValue('Mocked Review Tooltip');

        expect(component.tooltip).toBe('Mocked Review Tooltip');
    });

    it('should keep an icon on a REVIEW_COMMENT badge to tell it apart from the feedback count', () => {
        fixture.componentRef.setInput('badge', new FileBadge(FileBadgeType.REVIEW_COMMENT, 2));
        fixture.detectChanges();

        const pill: HTMLElement = fixture.nativeElement.querySelector('.file-browser-badge');
        expect(pill.textContent?.trim()).toBe('2');
        expect(pill.querySelector('fa-icon')).not.toBeNull();
    });

    it('should not have a tooltip for an unknown badge type', () => {
        fixture.componentRef.setInput('badge', new FileBadge('unknown' as FileBadgeType, 3));
        fixture.detectChanges();
        expect(component.tooltip).toBeUndefined();
    });
});

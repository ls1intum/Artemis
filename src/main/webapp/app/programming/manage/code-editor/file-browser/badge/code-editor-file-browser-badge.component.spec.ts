import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { FileBadge, FileBadgeType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorFileBrowserBadgeComponent } from 'app/programming/manage/code-editor/file-browser/badge/code-editor-file-browser-badge.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CodeEditorFileBrowserBadgeComponent', () => {
    let component: CodeEditorFileBrowserBadgeComponent;
    let fixture: ComponentFixture<CodeEditorFileBrowserBadgeComponent>;
    let translateService: TranslateService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        translateService = TestBed.inject(TranslateService);
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(CodeEditorFileBrowserBadgeComponent);
        component = fixture.componentInstance;
        component.badge = new FileBadge(FileBadgeType.FEEDBACK_SUGGESTION, 3);
        fixture.detectChanges();
    });

    it('should correctly display the tooltip for a FEEDBACK_SUGGESTION badge', () => {
        jest.spyOn(translateService, 'instant').mockReturnValue('Mocked Tooltip');
        expect(component.tooltip).toBe('Mocked Tooltip');
    });

    it('should return faLightbulb icon for a FEEDBACK_SUGGESTION badge', () => {
        expect(component.icon!.iconName).toBe('lightbulb');
    });

    it('should not have an icon for an unknown badge type', () => {
        component.badge = new FileBadge('unknown' as FileBadgeType, 3);
        fixture.changeDetectorRef.detectChanges();
        expect(component.icon).toBeUndefined();
    });

    it('should not have a tooltip for an unknown badge type', () => {
        component.badge = new FileBadge('unknown' as FileBadgeType, 3);
        fixture.changeDetectorRef.detectChanges();
        expect(component.tooltip).toBeUndefined();
    });
});

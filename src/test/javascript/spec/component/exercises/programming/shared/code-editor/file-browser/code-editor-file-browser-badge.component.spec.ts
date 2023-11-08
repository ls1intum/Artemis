import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FileBadge, FileBadgeType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorFileBrowserBadgeComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-badge.component';

describe('CodeEditorFileBrowserBadgeComponent', () => {
    let component: CodeEditorFileBrowserBadgeComponent;
    let fixture: ComponentFixture<CodeEditorFileBrowserBadgeComponent>;
    let translateService: TranslateService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FontAwesomeModule, NgbModule, TranslateModule.forRoot()],
            declarations: [CodeEditorFileBrowserBadgeComponent],
            providers: [TranslateService],
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
        fixture.detectChanges();
        expect(component.icon).toBeUndefined();
    });

    it('should not have a tooltip for an unknown badge type', () => {
        component.badge = new FileBadge('unknown' as FileBadgeType, 3);
        fixture.detectChanges();
        expect(component.tooltip).toBeUndefined();
    });
});

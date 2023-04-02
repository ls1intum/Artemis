import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PrivacyStatementUpdateComponent } from 'app/admin/privacy-statement/privacy-statement-update/privacy-statement-update.component';
import { MockComponent, MockDirective } from 'ng-mocks';
import { PrivacyStatementUnsavedChangesWarningComponent } from 'app/admin/privacy-statement/unsaved-changes-warning/privacy-statement-unsaved-changes-warning.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTestModule } from '../../test.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { MockLanguageHelper } from '../../helpers/mocks/service/mock-translate.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ModePickerComponent } from 'app/exercises/shared/mode-picker/mode-picker.component';

describe('PrivacyStatementUpdateComponent', () => {
    let component: PrivacyStatementUpdateComponent;
    let fixture: ComponentFixture<PrivacyStatementUpdateComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                PrivacyStatementUpdateComponent,
                MockComponent(PrivacyStatementUnsavedChangesWarningComponent),
                MockComponent(ButtonComponent),
                MockDirective(TranslateDirective),
                MockComponent(MarkdownEditorComponent),
                MockComponent(ModePickerComponent),
            ],
            providers: [
                { provide: JhiLanguageHelper, useClass: MockLanguageHelper },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PrivacyStatementUpdateComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});

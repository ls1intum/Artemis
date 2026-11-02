import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDialogRef } from '@tumaet/ui-angular';
import { vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IMPORT_DIALOG_BACK, ImportDialogFooterComponent } from 'app/course/manage/exercises/create-modal/import-dialog-footer.component';

describe('ImportDialogFooterComponent', () => {
    let fixture: ComponentFixture<ImportDialogFooterComponent>;
    let component: ImportDialogFooterComponent;
    let dialogRef: TumAetUiDialogRef;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ImportDialogFooterComponent],
            providers: [MockProvider(TumAetUiDialogRef), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ImportDialogFooterComponent);
        component = fixture.componentInstance;
        dialogRef = TestBed.inject(TumAetUiDialogRef);
    });

    it('closes the dialog with the back sentinel', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');

        component.back();

        expect(closeSpy).toHaveBeenCalledWith(IMPORT_DIALOG_BACK);
    });
});

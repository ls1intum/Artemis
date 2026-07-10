import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IMPORT_DIALOG_BACK, ImportDialogFooterComponent } from 'app/course/manage/exercises/create-modal/import-dialog-footer.component';

describe('ImportDialogFooterComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ImportDialogFooterComponent>;
    let component: ImportDialogFooterComponent;
    let dialogRef: DynamicDialogRef;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ImportDialogFooterComponent],
            providers: [MockProvider(DynamicDialogRef), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ImportDialogFooterComponent);
        component = fixture.componentInstance;
        dialogRef = TestBed.inject(DynamicDialogRef);
    });

    it('closes the dialog with the back sentinel', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');

        component.back();

        expect(closeSpy).toHaveBeenCalledWith(IMPORT_DIALOG_BACK);
    });
});

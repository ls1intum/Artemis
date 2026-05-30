import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { UsersImportButtonComponent } from 'app/shared-ui/user-import/button/users-import-button.component';
import { UsersImportDialogComponent } from 'app/shared-ui/user-import/dialog/users-import-dialog.component';

describe('UsersImportButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<UsersImportButtonComponent>;
    let comp: UsersImportButtonComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), UsersImportButtonComponent],
            providers: [MockProvider(AlertService)],
        })
            .overrideComponent(UsersImportButtonComponent, {
                set: {
                    imports: [MockComponent(ButtonComponent), MockComponent(UsersImportDialogComponent)],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UsersImportButtonComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        const openStudentsExamImportDialogButton = fixture.debugElement.query(By.css('jhi-button'));
        expect(openStudentsExamImportDialogButton).not.toBeNull();
    });

    it('should call open on dialog when openUsersImportDialog is called', () => {
        fixture.detectChanges();
        const mockDialog = { open: vi.fn() };
        vi.spyOn(comp, 'importDialog').mockReturnValue(mockDialog as any);

        comp.openUsersImportDialog(new MouseEvent('click'));

        expect(mockDialog.open).toHaveBeenCalledOnce();
    });

    it('should emit importDone when onImportCompleted is called', () => {
        const emitSpy = vi.spyOn(comp.importDone, 'emit');

        comp.onImportCompleted();

        expect(emitSpy).toHaveBeenCalledOnce();
    });
});

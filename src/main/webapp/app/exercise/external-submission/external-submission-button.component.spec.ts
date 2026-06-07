import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { ExternalSubmissionDialogComponent } from 'app/exercise/external-submission/external-submission-dialog.component';
import { ExternalSubmissionButtonComponent } from 'app/exercise/external-submission/external-submission-button.component';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ExternalSubmissionButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExternalSubmissionButtonComponent>;
    let dialogServiceOpenSpy: ReturnType<typeof vi.fn>;

    beforeEach(async () => {
        dialogServiceOpenSpy = vi.fn();

        await TestBed.configureTestingModule({
            imports: [ExternalSubmissionButtonComponent],
            providers: [{ provide: DialogService, useValue: { open: dialogServiceOpenSpy } }, { provide: TranslateService, useClass: MockTranslateService }, provideHttpClient()],
        }).compileComponents();

        fixture = TestBed.createComponent(ExternalSubmissionButtonComponent);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should open external submission dialog on click', () => {
        const exercise = { id: 1 } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);

        fixture.detectChanges();
        fixture.debugElement.query(By.css('.btn')).nativeElement.click();

        expect(dialogServiceOpenSpy).toHaveBeenCalledOnce();
        expect(dialogServiceOpenSpy).toHaveBeenCalledWith(
            ExternalSubmissionDialogComponent,
            expect.objectContaining({
                header: 'artemisApp.submission.createExternal',
                width: '50rem',
                modal: true,
                closable: true,
                closeOnEscape: true,
                dismissableMask: false,
                inputValues: { exercise },
            }),
        );
    });
});

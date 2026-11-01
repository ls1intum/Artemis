import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { TumAetUiButtonComponent, TumAetUiDialogService } from '@tumaet/ui-angular';
import { TranslateService } from '@ngx-translate/core';
import { ExternalSubmissionDialogComponent } from 'app/exercise/external-submission/external-submission-dialog.component';
import { ExternalSubmissionButtonComponent } from 'app/exercise/external-submission/external-submission-button.component';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('ExternalSubmissionButtonComponent', () => {
    let fixture: ComponentFixture<ExternalSubmissionButtonComponent>;
    let dialogServiceOpenSpy: ReturnType<typeof vi.fn>;

    beforeEach(async () => {
        dialogServiceOpenSpy = vi.fn();

        await TestBed.configureTestingModule({
            imports: [ExternalSubmissionButtonComponent],
            providers: [
                { provide: TumAetUiDialogService, useValue: { open: dialogServiceOpenSpy } },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExternalSubmissionButtonComponent);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it.each([false, true])('preserves the default appearance and supports the outlined toolbar button (%s)', (outlined) => {
        fixture.componentRef.setInput('exercise', { id: 1 } as Exercise);
        if (outlined) {
            fixture.componentRef.setInput('outlined', true);
        }
        fixture.detectChanges();

        const button = fixture.debugElement.query(By.directive(TumAetUiButtonComponent)).componentInstance as TumAetUiButtonComponent;
        expect(button.severity()).toBe(outlined ? 'secondary' : 'warn');
        expect(button.variant()).toBe(outlined ? 'outlined' : 'solid');
    });

    it('should open external submission dialog on click', () => {
        const exercise = { id: 1 } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);

        fixture.detectChanges();
        fixture.debugElement.query(By.css('button')).nativeElement.click();

        expect(dialogServiceOpenSpy).toHaveBeenCalledOnce();
        expect(dialogServiceOpenSpy).toHaveBeenCalledWith(
            ExternalSubmissionDialogComponent,
            expect.objectContaining({
                header: 'artemisApp.submission.createExternal',
                size: 'large',
                closable: true,
                closeOnEscape: true,
                dismissableMask: false,
                inputValues: { exercise },
            }),
        );
    });
});

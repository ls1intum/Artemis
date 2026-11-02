import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PresentationInstanceActionsComponent } from 'app/presentation/manage/presentation-instance-actions.component';
import { provideArtemisTumAetUiTranslator } from 'app/shared-ui/tum-aet-ui-integration/artemis-tumaet-ui-translator';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('PresentationInstanceActionsComponent', () => {
    let fixture: ComponentFixture<PresentationInstanceActionsComponent>;

    const query = (selector: string) => (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(selector);

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PresentationInstanceActionsComponent],
            providers: [provideArtemisTumAetUiTranslator(), { provide: TranslateService, useClass: MockTranslateService }, { provide: DialogService, useClass: MockDialogService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PresentationInstanceActionsComponent);
    });

    it('should offer the delete button only when deleting is allowed', () => {
        fixture.detectChanges();
        expect(query('[data-testid="edit-instance-button"]')).not.toBeNull();
        expect(query('[data-testid="delete-instance-button"]')).toBeNull();

        fixture.componentRef.setInput('canDelete', true);
        fixture.detectChanges();
        expect(query('[data-testid="delete-instance-button"]')).not.toBeNull();
    });

    it('should emit when edit is clicked', () => {
        const editSpy = vi.fn();
        fixture.componentInstance.editRequested.subscribe(editSpy);
        fixture.detectChanges();

        query('[data-testid="edit-instance-button"] button')!.click();

        expect(editSpy).toHaveBeenCalledOnce();
    });
});

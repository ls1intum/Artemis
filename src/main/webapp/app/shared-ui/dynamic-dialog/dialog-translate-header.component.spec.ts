import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { DynamicDialogConfig } from 'primeng/dynamicdialog';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DialogTranslateHeaderComponent } from 'app/shared-ui/dynamic-dialog/dialog-translate-header.component';

describe('DialogTranslateHeaderComponent', () => {
    const createComponent = async (data: DynamicDialogConfig['data']): Promise<ComponentFixture<DialogTranslateHeaderComponent>> => {
        await TestBed.configureTestingModule({
            imports: [DialogTranslateHeaderComponent],
            providers: [
                { provide: DynamicDialogConfig, useValue: { data } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        return TestBed.createComponent(DialogTranslateHeaderComponent);
    };

    it('reads the header key and params from the dialog config data', async () => {
        const fixture = await createComponent({ headerKey: 'artemisApp.exercise.delete.title', headerParams: { title: 'Foo' } });
        const component = fixture.componentInstance;

        expect(component['headerKey']).toBe('artemisApp.exercise.delete.title');
        expect(component['headerParams']).toEqual({ title: 'Foo' });
    });

    it('falls back to an empty header key and undefined params when the config has no data', async () => {
        const fixture = await createComponent(undefined);
        const component = fixture.componentInstance;

        expect(component['headerKey']).toBe('');
        expect(component['headerParams']).toBeUndefined();
    });
});

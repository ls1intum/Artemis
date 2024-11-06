import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { SwitchEditModeButtonComponent } from '../../../../../../main/webapp/app/exercises/programming/manage/update/switch-edit-mode-button/switch-edit-mode-button.component';
import { ArtemisSharedCommonModule } from '../../../../../../main/webapp/app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from '../../../../../../main/webapp/app/shared/components/shared-component.module';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { By } from '@angular/platform-browser';
import { MockModule } from 'ng-mocks';

describe('SwitchEditModeButtonComponent', () => {
    let fixture: ComponentFixture<SwitchEditModeButtonComponent>;
    let comp: SwitchEditModeButtonComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [SwitchEditModeButtonComponent],
            declarations: [MockModule(ArtemisSharedCommonModule), MockModule(ArtemisSharedComponentModule)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(SwitchEditModeButtonComponent);
                comp = fixture.componentInstance;

                fixture.componentRef.setInput('isSimpleMode', false);
            });
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should emit to call passed method when button is clicked', () => {
        const switchEditModeSpy = jest.spyOn(comp.switchEditMode, 'emit');

        const button = fixture.debugElement.query(By.css('jhi-button'));
        button.triggerEventHandler('onClick', null);

        expect(switchEditModeSpy).toHaveBeenCalledOnce();
    });
});

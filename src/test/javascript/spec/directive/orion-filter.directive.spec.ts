import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Component, DebugElement } from '@angular/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../test.module';
import { By } from '@angular/platform-browser';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { FormsModule } from '@angular/forms';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import * as sinon from 'sinon';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { TranslatePipeMock } from '../helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective } from 'ng-mocks';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { isOrion } from 'app/shared/orion/orion';

@Component({
    selector: 'jhi-test-component',
    template: '<div id="shown" jhiOrionFilter [showInOrionWindow]="true"></div><div id="hidden" jhiOrionFilter [showInOrionWindow]="false"></div>',
})
class TestComponent {
}

describe('OrionFilterDirective', () => {
    let comp: TestComponent;
    let fixture: ComponentFixture<TestComponent>;
    let debugElement: DebugElement;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [TestComponent, OrionFilterDirective],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    it('should show/hide elements if isOrion is true', fakeAsync(() => {
        //@ts-ignore
        isOrion = true;

        fixture.detectChanges();
        tick();

        const shownDiv = debugElement.query(By.css('#shown'));
        expect(shownDiv).not.toBe(null);
        expect(shownDiv.nativeElement.style.display).toBe("");

        const hiddenDiv = debugElement.query(By.css('#hidden'));
        expect(hiddenDiv).not.toBe(null);
        expect(hiddenDiv.nativeElement.style.display).toBe("none");
    }));

    it('should show/hide elements if isOrion is false', fakeAsync(() => {
        //@ts-ignore
        isOrion = false;

        fixture.detectChanges();
        tick();

        const shownDiv = debugElement.query(By.css('#shown'));
        expect(shownDiv).not.toBe(null);
        expect(shownDiv.nativeElement.style.display).toBe("none");

        const hiddenDiv = debugElement.query(By.css('#hidden'));
        expect(hiddenDiv).not.toBe(null);
        expect(hiddenDiv.nativeElement.style.display).toBe("");
    }));
});

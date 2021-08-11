import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { CookieService } from 'ngx-cookie-service';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { DebugElement, SimpleChange } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonStub, spy, stub } from 'sinon';
import * as sinon from 'sinon';

import { AceEditorModule } from 'ng2-ace-editor';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { ArtemisTestModule } from '../../test.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';

import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { MockCodeEditorConflictStateService } from '../../helpers/mocks/service/mock-code-editor-conflict-state.service';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockCodeEditorRepositoryService } from '../../helpers/mocks/service/mock-code-editor-repository.service';
import { MockCookieService } from '../../helpers/mocks/service/mock-cookie.service';
import { CodeEditorGridComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-grid.component';
import { Interactable } from '@interactjs/core/Interactable';
import { InteractableEvent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';

chai.use(sinonChai);
const expect = chai.expect;

//dummy based on original event browser output
/*
const toggleEventFileBrowser : PointerEvent = {
    height: 1,
}
 */

describe('CodeEditorGridComponent', () => {
    let comp: CodeEditorGridComponent;
    let fixture: ComponentFixture<CodeEditorGridComponent>;
    let debugElement: DebugElement;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, AceEditorModule, FeatureToggleModule],
            declarations: [CodeEditorGridComponent],
            providers: [
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorConflictStateService, useClass: MockCodeEditorConflictStateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: CookieService, useClass: MockCookieService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorGridComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    it('should hide draggable icons', () => {
        fixture.detectChanges();
        let draggableIconForFileBrowser = fixture.debugElement.query(By.css('#draggableIconForFileBrowser'));

        expect(draggableIconForFileBrowser).to.exist;

        const fileBrowserInteractable: Interactable = { target: '.resizable-filebrowser' } as Interactable;
        const iconSVGName = {} as unknown as HTMLElement;
        //iconSVGName.blur();
        //spy(iconSVGName, "blur");
        //sinon.mock(iconSVGName).expects("blur()");
        //sinon.stub(iconSVGName, "blur()");
        //const pointerEvent : PointerEvent = {type: "click", target: iconSVGName} as unknown as PointerEvent;
        const pointerEvent: PointerEvent = { type: 'click' } as unknown as PointerEvent;
        const fileBrowserCollapseEvent: InteractableEvent = { event: pointerEvent, horizontal: true, interactable: fileBrowserInteractable };

        sinon.stub(fileBrowserInteractable, 'resizable');

        comp.toggleCollapse(fileBrowserCollapseEvent);
        expect(comp.fileBrowserIsCollapsed).to.be.false;

        //comp.fileBrowserIsCollapsed = true;
        fixture.detectChanges();

        draggableIconForFileBrowser = fixture.debugElement.query(By.css('#draggableIconForFileBrowser'));
        expect(comp.fileBrowserIsCollapsed).to.be.true;
        expect(draggableIconForFileBrowser).not.to.exist;
    });
});

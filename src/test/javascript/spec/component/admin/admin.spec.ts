import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateModule } from '@ngx-translate/core';
import { WindowRef } from 'app/core';
import { DebugElement, EventEmitter, SimpleChange, SimpleChanges } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewModule } from 'ngx-treeview';
import { SinonStub, spy, stub } from 'sinon';
import { Subject } from 'rxjs';
import { CodeEditorAceComponent, CodeEditorFileService, CodeEditorRepositoryFileService } from 'app/code-editor';
import { ArtemisTestModule } from '../../test.module';
import { MockCodeEditorRepositoryFileService } from '../../mocks';
import { CreateFileChange, FileType, RenameFileChange } from 'app/entities/ace-editor/file-change.model';
import { AnnotationArray } from 'app/entities/ace-editor';
import { AuditsComponent } from 'app/admin';
import { ArtemisAdminModule } from 'app/admin/admin.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('AdminModule', () => {
    let comp: AuditsComponent;
    let fixture: ComponentFixture<AuditsComponent>;
    let debugElement: DebugElement;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisAdminModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AuditsComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    // The admin module is lazy loaded - we therefore need a dummy test to load the module and verify that there are no dependency related issues.
    it('should render a component from the admin module', () => {
        expect(comp).to.exist;
    });
});

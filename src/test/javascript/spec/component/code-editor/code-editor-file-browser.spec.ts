import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewModule } from 'ngx-treeview';
import { spy, stub, SinonStub } from 'sinon';
import { Observable, Subject, throwError } from 'rxjs';
import { isEqual as _isEqual } from 'lodash';
import { CodeEditorFileBrowserComponent, CodeEditorRepositoryFileService, CodeEditorRepositoryService, CodeEditorStatusComponent } from 'app/code-editor';
import { ArTEMiSTestModule } from '../../test.module';
import { MockCodeEditorRepositoryFileService, MockCodeEditorRepositoryService } from '../../mocks';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorActionsComponent', () => {
    let comp: CodeEditorFileBrowserComponent;
    let fixture: ComponentFixture<CodeEditorFileBrowserComponent>;
    let debugElement: DebugElement;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let codeEditorRepositoryService: CodeEditorRepositoryService;
    // let updateFilesStub: SinonStub;
    // let commitStub: SinonStub;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, AceEditorModule, TreeviewModule],
            declarations: [CodeEditorFileBrowserComponent, CodeEditorStatusComponent],
            providers: [
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorFileBrowserComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                codeEditorRepositoryFileService = debugElement.injector.get(CodeEditorRepositoryFileService);
                codeEditorRepositoryService = debugElement.injector.get(CodeEditorRepositoryService);
                // updateFilesStub = stub(codeEditorRepositoryFileService, 'updateFiles');
                // commitStub = stub(codeEditorRepositoryService, 'commit');
            });
    });

    afterEach(updateFilesStub => {
        /*        updateFilesStub.restore();
        commitStub.restore();*/
    });

    it('should render', () => {
        expect(true).to.be.true;
    });
});

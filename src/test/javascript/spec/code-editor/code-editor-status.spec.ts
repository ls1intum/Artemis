import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { BrowserDynamicTestingModule } from '@angular/platform-browser-dynamic/testing';
import { AceEditorModule } from 'ng2-ace-editor';
// import { ArTEMiSTestModule } from '../test.module';
import { CodeEditorStatusComponent } from '../../../../main/webapp/app/code-editor';
import { ArTEMiSCodeEditorModule } from '../../../../main/webapp/app/code-editor';
import { CommitState } from '../../../../main/webapp/app/entities/ace-editor';
import { ArTEMiSTestModule } from '../test.module';
import { ArTEMiSHomeModule } from '../../../../main/webapp/app/home';
import { TranslateModule } from '@ngx-translate/core';
import { RouterModule } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('Component Tests', () => {
    describe('SettingsComponent', () => {
        let comp: CodeEditorStatusComponent;
        let fixture: ComponentFixture<CodeEditorStatusComponent>;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                // imports: [TranslateModule.forRoot(), RouterModule.forRoot(), ArTEMiSCodeEditorModule, AceEditorModule, ArTEMiSHomeModule],
                imports: [TranslateModule.forRoot(), ArTEMiSTestModule, AceEditorModule],
                declarations: [CodeEditorStatusComponent],
                // providers: [
                //     {
                //         provide: JhiTrackerService,
                //         useClass: MockTrackerService,
                //     },
                // ],
            })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(CodeEditorStatusComponent);
                    comp = fixture.componentInstance;
                });
        }));

        it('should show initial states when loaded without parameters', function() {
            comp.commitState = CommitState.COMMITTING;
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('p').textContent).toEqual('test');
        });
    });
});

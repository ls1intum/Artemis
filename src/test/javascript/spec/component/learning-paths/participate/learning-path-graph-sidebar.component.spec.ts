import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { ArtemisTestModule } from '../../../test.module';
import { LearningPathGraphSidebarComponent } from 'app/course/learning-paths/participate/learning-path-graph-sidebar.component';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';

describe('LearningPathGraphSidebarComponent', () => {
    let fixture: ComponentFixture<LearningPathGraphSidebarComponent>;
    let comp: LearningPathGraphSidebarComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockComponent(LearningPathGraphComponent), MockPipe(ArtemisTranslatePipe), NgbTooltipMocksModule],
            declarations: [LearningPathGraphSidebarComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathGraphSidebarComponent);
                comp = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    it('should create', () => {
        expect(fixture).toBeTruthy();
        expect(comp).toBeTruthy();
    });

    it('should show graph when not collapsed', () => {
        comp.collapsed = false;
        fixture.detectChanges();
        const expanded = fixture.debugElement.query(By.css('.expanded-graph'));
        const collapsed = fixture.debugElement.query(By.css('.collapsed-graph'));
        expect(expanded.nativeElement.hasAttribute('hidden')).toBeFalsy();
        expect(collapsed.nativeElement.hasAttribute('hidden')).toBeTruthy();
    });

    it('should not show graph when collapsed', () => {
        comp.collapsed = true;
        fixture.detectChanges();
        const expanded = fixture.debugElement.query(By.css('.expanded-graph'));
        const collapsed = fixture.debugElement.query(By.css('.collapsed-graph'));
        expect(expanded.nativeElement.hasAttribute('hidden')).toBeTruthy();
        expect(collapsed.nativeElement.hasAttribute('hidden')).toBeFalsy();
    });
});

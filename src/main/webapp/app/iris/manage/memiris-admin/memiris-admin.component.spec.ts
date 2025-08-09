import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MemirisAdminComponent } from './memiris-admin.component';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MemirisGraphSettingsComponent } from 'app/iris/shared/memiris-graph-settings/memiris-graph-settings.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { MemirisGraphData, MemirisGraphSettings, MemirisSimulationNode } from 'app/iris/shared/entities/memiris.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

// Workaround for https://github.com/help-me-mom/ng-mocks/issues/8634
@Component({
    selector: 'jhi-memiris-graph-view',
    template: '<div>Mock Graph View</div>',
})
class MockMemirisGraphViewComponent {
    @Input() graphData?: MemirisGraphData;
    @Input() settings = new MemirisGraphSettings();
    @Input() selectedNode?: MemirisSimulationNode;
    @Output() nodeSelected = new EventEmitter<MemirisSimulationNode>();
}

describe('MemirisAdminComponent', () => {
    let component: MemirisAdminComponent;
    let fixture: ComponentFixture<MemirisAdminComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MemirisAdminComponent],
            declarations: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe), MockMemirisGraphViewComponent, MockComponent(MemirisGraphSettingsComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(MemirisAdminComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should initialize with default settings', () => {
        expect(component.settings()).toEqual(new MemirisGraphSettings());
    });

    it('should initialize with empty graph data after 1 second', fakeAsync(() => {
        component.ngOnInit();

        // Fast-forward time by 1 second
        tick(1000);

        // Should have empty graph data
        expect(component.graphData).toEqual(new MemirisGraphData([], [], []));
    }));

    it('should populate graph data with mock data after 2 seconds', fakeAsync(() => {
        component.ngOnInit();

        // Fast-forward time by 2 seconds
        tick(2000);

        // Graph data should be populated
        expect(component.graphData).toBeDefined();
        expect(component.graphData!.memories.length).toBeGreaterThan(0);
        expect(component.graphData!.learnings.length).toBeGreaterThan(0);
    }));

    it('should create connections between memories', fakeAsync(() => {
        component.graphData = undefined;
        component.ngOnInit();

        // Fast-forward time
        tick(2000);

        // Check that connections exist
        expect(component.graphData).toBeDefined();
        expect(component.graphData!.connections.length).toBeGreaterThan(0);

        // Check that each connection has at least 2 memories
        component.graphData!.connections.forEach((connection) => {
            expect(connection.memories.length).toBeGreaterThanOrEqual(2);
        });
    }));
});

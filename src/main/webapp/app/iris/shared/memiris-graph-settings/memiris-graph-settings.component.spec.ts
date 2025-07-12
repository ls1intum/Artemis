import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockPipe } from 'ng-mocks';
import { MemirisGraphSettingsComponent } from './memiris-graph-settings.component';
import { MemirisGraphSettings } from '../entities/memiris.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('MemirisGraphSettingsComponent', () => {
    let component: MemirisGraphSettingsComponent;
    let fixture: ComponentFixture<MemirisGraphSettingsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MemirisGraphSettingsComponent, FormsModule, FontAwesomeModule],
            declarations: [MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();

        fixture = TestBed.createComponent(MemirisGraphSettingsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with default MemirisGraphSettings values', () => {
        const defaultSettings = new MemirisGraphSettings();

        expect(component.settings()).toEqual(defaultSettings);
        expect(component.settings().showMemories).toBeTrue();
        expect(component.settings().showMemoryLabels).toBeTrue();
        expect(component.settings().hideDeleted).toBeTrue();
        expect(component.settings().showLearnings).toBeTrue();
        expect(component.settings().showLearningLabels).toBeTrue();
        expect(component.settings().showConnections).toBeTrue();
    });

    it('should update settings correctly when updateSetting is called', () => {
        // Prepare a mock event with a checked property
        const mockEvent = { target: { checked: false } };

        // Test updating showMemories setting
        component.updateSetting(mockEvent, 'showMemories');
        expect(component.settings().showMemories).toBeFalse();

        // Test updating showMemoryLabels setting
        component.updateSetting(mockEvent, 'showMemoryLabels');
        expect(component.settings().showMemoryLabels).toBeFalse();

        // Test updating hideDeleted setting
        component.updateSetting(mockEvent, 'hideDeleted');
        expect(component.settings().hideDeleted).toBeFalse();

        // Test updating showLearnings setting
        component.updateSetting(mockEvent, 'showLearnings');
        expect(component.settings().showLearnings).toBeFalse();

        // Test updating showLearningLabels setting
        component.updateSetting(mockEvent, 'showLearningLabels');
        expect(component.settings().showLearningLabels).toBeFalse();

        // Test updating showConnections setting
        component.updateSetting(mockEvent, 'showConnections');
        expect(component.settings().showConnections).toBeFalse();

        // Test toggling a setting back to true
        const mockEventTrue = { target: { checked: true } };
        component.updateSetting(mockEventTrue, 'showMemories');
        expect(component.settings().showMemories).toBeTrue();
    });

    it('should handle updates with undefined checked value by using current setting value', () => {
        // Get initial value of showMemories
        const initialValue = component.settings().showMemories;

        // Create an event with undefined checked property
        const mockEventUndefined = { target: {} };

        // Call updateSetting with the undefined event
        component.updateSetting(mockEventUndefined, 'showMemories');

        // Value should remain unchanged
        expect(component.settings().showMemories).toBe(initialValue);
    });

    it('should correctly maintain other settings when updating a specific one', () => {
        // Initial state should be all true
        expect(component.settings().showMemories).toBeTrue();
        expect(component.settings().showLearnings).toBeTrue();

        // Update only showMemories
        const mockEvent = { target: { checked: false } };
        component.updateSetting(mockEvent, 'showMemories');

        // showMemories should be false, but showLearnings should still be true
        expect(component.settings().showMemories).toBeFalse();
        expect(component.settings().showLearnings).toBeTrue();
    });
});

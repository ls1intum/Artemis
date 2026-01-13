import { TestBed } from '@angular/core/testing';
import { UserSettingsTitleBarComponent } from './user-settings-title-bar.component';
import { UserSettingsTitleBarService } from '../user-settings-title-bar.service';

describe('UserSettingsTitleBarComponent', () => {
    let component: UserSettingsTitleBarComponent;
    let titleBarService: UserSettingsTitleBarService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UserSettingsTitleBarComponent],
            providers: [UserSettingsTitleBarService],
        })
            .overrideTemplate(UserSettingsTitleBarComponent, '')
            .compileComponents();

        const fixture = TestBed.createComponent(UserSettingsTitleBarComponent);
        component = fixture.componentInstance;
        titleBarService = TestBed.inject(UserSettingsTitleBarService);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should expose title template from service via customTitleTemplate', () => {
        const mockTemplate = {} as any;
        titleBarService.setTitleTemplate(mockTemplate);

        expect(component.customTitleTemplate()).toBe(mockTemplate);
    });

    it('should expose actions template from service via customActionsTemplate', () => {
        const mockTemplate = {} as any;
        titleBarService.setActionsTemplate(mockTemplate);

        expect(component.customActionsTemplate()).toBe(mockTemplate);
    });

    it('should expose both templates when both are set', () => {
        const titleTemplate = {} as any;
        const actionsTemplate = {} as any;
        titleBarService.setTitleTemplate(titleTemplate);
        titleBarService.setActionsTemplate(actionsTemplate);

        expect(component.customTitleTemplate()).toBe(titleTemplate);
        expect(component.customActionsTemplate()).toBe(actionsTemplate);
    });

    it('should return undefined when templates are not set', () => {
        titleBarService.setTitleTemplate(undefined);
        titleBarService.setActionsTemplate(undefined);

        expect(component.customTitleTemplate()).toBeUndefined();
        expect(component.customActionsTemplate()).toBeUndefined();
    });
});

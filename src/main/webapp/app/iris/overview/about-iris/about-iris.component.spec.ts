import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { AboutIrisComponent } from './about-iris.component';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { faRobot } from '@fortawesome/free-solid-svg-icons';

describe('AboutIrisComponent', () => {
    setupTestBed({ zoneless: true });

    let component: AboutIrisComponent;
    let fixture: ComponentFixture<AboutIrisComponent>;
    let compiled: DebugElement;

    beforeEach(async () => {
        vi.spyOn(console, 'warn').mockImplementation(() => {});
        TestBed.configureTestingModule({
            imports: [AboutIrisComponent, MockComponent(IrisLogoComponent), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(AboutIrisComponent);
        component = fixture.componentInstance;
        compiled = fixture.debugElement;
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should have correct component properties', () => {
        expect(component.faRobot).toBe(faRobot);
        expect(component.bulletPoints).toBeDefined();
        expect(component.bulletPoints['1']).toBe(2);
        expect(component.bulletPoints['2']).toBe(5);
        expect(component.bulletPoints['3']).toBe(3);
        expect(component.bulletPoints['4']).toBe(5);
        expect(component.objectKeys).toBe(Object.keys);
        expect(component.array).toBe(Array);
    });

    it('should render all required template elements', () => {
        const logoComponent = compiled.query(By.css('jhi-iris-logo'));
        const titleElement = compiled.query(By.css('h2'));
        const subheadings = compiled.queryAll(By.css('h4'));
        const bulletLists = compiled.queryAll(By.css('ul'));

        expect(logoComponent).toBeTruthy();
        expect(titleElement).toBeTruthy();
        expect(subheadings).toHaveLength(4);
        expect(bulletLists).toHaveLength(4);
    });

    it('should render correct total number of list items', () => {
        const expectedTotal = Object.values(component.bulletPoints).reduce((sum, count) => sum + count, 0);
        const listItems = compiled.queryAll(By.css('li'));
        expect(listItems).toHaveLength(expectedTotal);
    });
});

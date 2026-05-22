import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SearchableEntityItemComponent } from './searchable-entity-item.component';
import { SearchableEntity } from '../../../models/searchable-entity.model';
import { faCube } from '@fortawesome/free-solid-svg-icons';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('SearchableEntityItemComponent', () => {
    setupTestBed({ zoneless: true });

    let component: SearchableEntityItemComponent;
    let fixture: ComponentFixture<SearchableEntityItemComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SearchableEntityItemComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(SearchableEntityItemComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('entity', { id: 'test', title: 'Test', description: 'Desc', icon: faCube } as SearchableEntity);
        fixture.componentRef.setInput('isSelected', false);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should emit entityClick when clicked', () => {
        const spy = vi.spyOn(component.entityClick, 'emit');
        component['onClick']();
        expect(spy).toHaveBeenCalledWith(component.entity());
    });
});

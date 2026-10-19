import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { MockComponent } from 'ng-mocks';
import { AddAuxiliaryRepositoryButtonComponent } from 'app/programming/manage/update/add-auxiliary-repository-button.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { provideHttpClient } from '@angular/common/http';

describe('AddAuxiliaryRepositoryButtonComponent', () => {
    let comp: AddAuxiliaryRepositoryButtonComponent;
    let fixture: ComponentFixture<AddAuxiliaryRepositoryButtonComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockComponent(ButtonComponent)],
            providers: [provideHttpClient(), provideTranslateService()],
        });
        fixture = TestBed.createComponent(AddAuxiliaryRepositoryButtonComponent);
        comp = fixture.componentInstance;

        fixture.componentRef.setInput('programmingExercise', new ProgrammingExercise(undefined, undefined));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).toBeDefined();
    });

    it('should add auxiliary repository', () => {
        const initialLength = comp.programmingExercise().auxiliaryRepositories?.length || 0;
        const onRefreshSpy = vi.spyOn(comp.onRefresh, 'emit');

        comp.addAuxiliaryRepositoryRow();

        expect(comp.programmingExercise().auxiliaryRepositories?.length).toBe(initialLength + 1);
        expect(onRefreshSpy).toHaveBeenCalledOnce();
    });
});

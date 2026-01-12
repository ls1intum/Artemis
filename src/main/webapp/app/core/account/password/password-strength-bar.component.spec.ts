/**
 * Vitest tests for PasswordStrengthBarComponent.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { By } from '@angular/platform-browser';
import { PasswordStrengthBarComponent } from 'app/core/account/password/password-strength-bar.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Component Tests', () => {
    describe('PasswordStrengthBarComponent', () => {
        setupTestBed({ zoneless: true });

        let comp: PasswordStrengthBarComponent;
        let fixture: ComponentFixture<PasswordStrengthBarComponent>;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [PasswordStrengthBarComponent],
                providers: [{ provide: TranslateService, useClass: MockTranslateService }],
            }).compileComponents();
        });

        beforeEach(() => {
            fixture = TestBed.createComponent(PasswordStrengthBarComponent);
            comp = fixture.componentInstance;
        });

        describe('PasswordStrengthBarComponents', () => {
            it('should initialize with default values', () => {
                expect(comp.calculateStrengthScore('')).toBe(0);
                expect(comp.strengthColors).toEqual(['#F00', '#F90', '#FF0', '#9F0', '#0F0']);
                expect(comp.getStrengthColorAndLevel(0).filledSegments).toBe(1);
                expect(comp.getStrengthColorAndLevel(0).color).toBe(comp.strengthColors[0]);
            });

            it('should increase strength upon password value change', () => {
                expect(comp.calculateStrengthScore('')).toBe(0);
                expect(comp.calculateStrengthScore('aa')).toBeGreaterThanOrEqual(comp.calculateStrengthScore(''));
                expect(comp.calculateStrengthScore('aa^6')).toBeGreaterThanOrEqual(comp.calculateStrengthScore('aa'));
                expect(comp.calculateStrengthScore('Aa090(**)')).toBeGreaterThanOrEqual(comp.calculateStrengthScore('aa^6'));
                expect(comp.calculateStrengthScore('Aa090(**)+-07365')).toBeGreaterThanOrEqual(comp.calculateStrengthScore('Aa090(**)'));
            });

            it('should change the color based on strength', () => {
                expect(comp.getStrengthColorAndLevel(0).color).toBe(comp.strengthColors[0]);
                expect(comp.getStrengthColorAndLevel(11).color).toBe(comp.strengthColors[1]);
                expect(comp.getStrengthColorAndLevel(22).color).toBe(comp.strengthColors[2]);
                expect(comp.getStrengthColorAndLevel(33).color).toBe(comp.strengthColors[3]);
                expect(comp.getStrengthColorAndLevel(44).color).toBe(comp.strengthColors[4]);
            });
            it('should set color in correct amount on password change', () => {
                const testValues = ['aa', 'aabb^65+', 'Aa090(**)+-07365'];
                const expectedResults = [
                    { filledSegments: 1, color: 'rgb(255, 0, 0)' },
                    { filledSegments: 4, color: 'rgb(153, 255, 0)' },
                    { filledSegments: 5, color: 'rgb(0, 255, 0)' },
                ];

                testValues.forEach((testValue, index) => {
                    fixture.componentRef.setInput('passwordToCheck', testValue);
                    fixture.detectChanges();
                    const filteredPoints = fixture.debugElement.queryAll(By.css('.point')).filter((element) => {
                        return element.styles['background-color'] === expectedResults[index].color;
                    });
                    expect(filteredPoints).toHaveLength(expectedResults[index].filledSegments);
                });
            });
        });
    });
});

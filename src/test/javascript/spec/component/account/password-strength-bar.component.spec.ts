import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { PasswordStrengthBarComponent } from 'app/account/password/password-strength-bar.component';

describe('Component Tests', () => {
    describe('PasswordStrengthBarComponent', () => {
        let comp: PasswordStrengthBarComponent;
        let fixture: ComponentFixture<PasswordStrengthBarComponent>;

        beforeEach(() => {
            TestBed.configureTestingModule({
                declarations: [PasswordStrengthBarComponent],
            }).compileComponents();
        });

        beforeEach(() => {
            fixture = TestBed.createComponent(PasswordStrengthBarComponent);
            comp = fixture.componentInstance;
        });

        describe('PasswordStrengthBarComponents', () => {
            it('should initialize with default values', () => {
                expect(comp.measureStrength('')).toBe(0);
                expect(comp.colors).toEqual(['#F00', '#F90', '#FF0', '#9F0', '#0F0']);
                expect(comp.getColor(0).idx).toBe(1);
                expect(comp.getColor(0).color).toBe(comp.colors[0]);
            });

            it('should increase strength upon password value change', () => {
                expect(comp.measureStrength('')).toBe(0);
                expect(comp.measureStrength('aa')).toBeGreaterThanOrEqual(comp.measureStrength(''));
                expect(comp.measureStrength('aa^6')).toBeGreaterThanOrEqual(comp.measureStrength('aa'));
                expect(comp.measureStrength('Aa090(**)')).toBeGreaterThanOrEqual(comp.measureStrength('aa^6'));
                expect(comp.measureStrength('Aa090(**)+-07365')).toBeGreaterThanOrEqual(comp.measureStrength('Aa090(**)'));
            });

            it('should change the color based on strength', () => {
                expect(comp.getColor(0).color).toBe(comp.colors[0]);
                expect(comp.getColor(11).color).toBe(comp.colors[1]);
                expect(comp.getColor(22).color).toBe(comp.colors[2]);
                expect(comp.getColor(33).color).toBe(comp.colors[3]);
                expect(comp.getColor(44).color).toBe(comp.colors[4]);
            });
            it('should set color in correct amount on password change', () => {
                const testValues = ['aa', 'aabb^65+', 'Aa090(**)+-07365'];
                const expectedResults = [
                    { idx: 1, color: 'rgb(255, 0, 0)' },
                    { idx: 4, color: 'rgb(153, 255, 0)' },
                    { idx: 5, color: 'rgb(0, 255, 0)' },
                ];

                testValues.forEach((testValue, index) => {
                    comp.passwordToCheck = testValue;
                    fixture.detectChanges();
                    const filteredPoints = fixture.debugElement.queryAll(By.css('.point')).filter((element) => {
                        return element.styles['background-color'] === expectedResults[index].color;
                    });
                    expect(filteredPoints).toHaveLength(expectedResults[index].idx);
                });
            });
        });
    });
});

import { readFileSync } from 'node:fs';
import { URL } from 'node:url';
import { describe, expect, it } from 'vitest';
import { JSDOM } from 'jsdom';
import { compileString } from 'sass';
import scss from 'postcss-scss';

const globalStyles = scss.parse(readFileSync(new URL('../src/main/webapp/content/scss/global.scss', import.meta.url), 'utf8'));
const validityRules = globalStyles.nodes.filter((node) => node.type === 'rule' && node.toString().includes('$ng-valid-border'));
const css = compileString(`$ng-valid-border: green; $ng-invalid-border: red; ${validityRules.join('\n')}`).css;

function border(markup) {
    const dom = new JSDOM(`<style>${css}</style>${markup}`);
    const field = dom.window.document.querySelector('#field');
    const style = dom.window.getComputedStyle(field);
    const result = { left: style.borderLeftWidth, right: style.borderRightWidth, color: style.borderLeftColor };
    dom.window.close();
    return result;
}

describe('legacy form validity accents', () => {
    it.each(['input', 'textarea', 'select'])('preserves required valid and interacted invalid native %s fields', (tag) => {
        expect(border(`<${tag} id="field" required class="ng-valid"></${tag}>`)).toMatchObject({ left: '5px', color: 'rgb(0, 128, 0)' });
        for (const state of ['ng-dirty', 'ng-touched']) {
            expect(border(`<${tag} id="field" class="ng-invalid ${state}"></${tag}>`)).toMatchObject({ left: '5px', color: 'rgb(255, 0, 0)' });
        }
        expect(border(`<${tag} id="field" class="ng-invalid ng-pristine ng-untouched"></${tag}>`).left).not.toBe('5px');
    });

    it('keeps checkbox borders thin and PrimeNG native fields covered', () => {
        expect(border('<input id="field" type="checkbox" class="form-check-input ng-invalid ng-touched">')).toMatchObject({ left: '1px', right: '1px' });
        expect(border('<input id="field" pInputText required class="ng-valid">').left).toBe('5px');
        expect(border('<input id="field" class="required ng-valid">').left).toBe('5px');
    });

    it('leaves TUM UI public native directives and custom controls untouched without reading private classes', () => {
        for (const field of ['input tumUiInput', 'textarea tumUiInput', 'textarea tumUiTextarea', 'tum-ui-select', 'tum-ui-checkbox']) {
            expect(border(`<${field} id="field" required class="ng-valid ng-invalid ng-touched"></${field.split(' ')[0]}>`).left).not.toBe('5px');
        }
    });

    it('keeps legacy custom controls covered without putting accents on form containers', () => {
        expect(border('<legacy-control id="field" class="form-control ng-invalid ng-touched"></legacy-control>').left).toBe('5px');
        expect(border('<form id="field" class="form-control ng-invalid ng-touched"></form>').left).not.toBe('5px');
        expect(border('<div id="field" class="ng-invalid ng-touched"><input class="ng-invalid"></div>').left).not.toBe('5px');
        expect(border('<legacy-control id="field" class="form-control ng-invalid ng-touched"><input class="ng-invalid"></legacy-control>').left).not.toBe('5px');
    });
});

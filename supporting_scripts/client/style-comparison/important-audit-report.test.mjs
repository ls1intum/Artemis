import assert from 'node:assert/strict';
import { test } from 'node:test';
import { classify, normalise, observe } from './important-audit-report.mjs';

const entry = (overrides) => ({ selector: '.probe', scope: '.probe', property: 'color', context: '', matched: 1, changed: 0, example: '', ...overrides });
const declaration = (overrides) => ({ file: 'a.css', line: 1, selector: '.probe', property: 'color', ...overrides });
const audit = (declarations, ...pages) => {
    const observations = new Map();
    pages.forEach((entries, index) => observe(observations, entries, `page ${index}`));
    return classify(declarations, observations);
};

test('a declaration that matched elements and changed none changes nothing', () => {
    const groups = audit([declaration()], [entry()]);
    assert.equal(groups['changes nothing'].length, 1);
});

test('a declaration that changed an element is needed and names the page', () => {
    const groups = audit([declaration()], [entry({ changed: 1, example: 'div.probe' })]);
    assert.deepEqual(groups.needed, ['a.css:1  .probe { color }  (div.probe on page 0)']);
});

test('an inactive rule does not borrow the matches of an active rule with the same selector and property', () => {
    const groups = audit([declaration({ line: 1 }), declaration({ line: 9 })], [entry(), entry({ context: '@media print ', matched: 0 })]);
    assert.equal(groups.ambiguous.length, 2);
    assert.equal(groups['changes nothing'].length, 0);
});

test('the same selector and property in the stylesheets of two components stays apart', () => {
    const groups = audit([declaration({ file: 'a.css' })], [entry({ scope: '.probe[_ngcontent-aaa]', matched: 1 }), entry({ scope: '.probe[_ngcontent-bbb]', matched: 0 })]);
    assert.equal(groups.ambiguous.length, 1);
    assert.equal(groups['changes nothing'].length, 0);
});

test('a longhand and its shorthand under one selector are ambiguous', () => {
    const groups = audit([declaration({ property: 'padding' }), declaration({ property: 'padding-top', line: 2 })], [entry({ property: 'padding-top' })]);
    assert.equal(groups.ambiguous.length, 2);
});

test('the same rule seen on several pages is not ambiguous', () => {
    const groups = audit([declaration()], [entry()], [entry({ matched: 3 })]);
    assert.equal(groups['changes nothing'].length, 1);
});

test('a shorthand is judged by its longhands', () => {
    const groups = audit([declaration({ property: 'padding' })], [entry({ property: 'padding-top', changed: 1 }), entry({ property: 'padding-left' })]);
    assert.equal(groups.needed.length, 1);
});

test('a declaration without matches or about an animation is not judged', () => {
    const groups = audit(
        [declaration({ line: 1 }), declaration({ selector: '.spin', property: 'animation', line: 2 })],
        [entry({ matched: 0 }), entry({ selector: '.spin', property: 'animation' })],
    );
    assert.equal(groups['matched no element'].length, 2);
});

test('a selector of the source and the one of the browser get one spelling', () => {
    assert.equal(normalise(':host(.a)   >   .b'), '.a:host>.b');
    assert.equal(normalise(':host ::ng-deep .b'), ':host .b');
});

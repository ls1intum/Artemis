import assert from 'node:assert/strict';
import { test } from 'node:test';

import { mergeSlowQueryReports } from '../../src/test/playwright/init/mergeSlowQueryReports.ts';

const report = (overrides = {}) => ({
    repeatedQueries: ['repeated'],
    truncated: false,
    ...overrides,
});

test('combines the findings of every node', () => {
    const merged = mergeSlowQueryReports([report(), report({ repeatedQueries: ['other'] })]);

    assert.deepEqual(merged.repeatedQueries, ['repeated', 'other']);
    assert.equal(merged.truncated, false);
});

test('is truncated when a later node is truncated', () => {
    assert.equal(mergeSlowQueryReports([report(), report({ truncated: true })]).truncated, true);
});

test('is truncated when the first node is truncated', () => {
    assert.equal(mergeSlowQueryReports([report({ truncated: true }), report()]).truncated, true);
});

test('is truncated when a middle node is truncated', () => {
    assert.equal(mergeSlowQueryReports([report(), report({ truncated: true }), report()]).truncated, true);
});

test('keeps the report of a single node', () => {
    assert.equal(mergeSlowQueryReports([report({ truncated: true })]).truncated, true);
});

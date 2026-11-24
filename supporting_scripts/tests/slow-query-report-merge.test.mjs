import assert from 'node:assert/strict';
import { test } from 'node:test';

import { mergeSlowQueryReports } from '../../src/test/playwright/init/mergeSlowQueryReports.ts';

const report = (overrides = {}) => ({
    slowQueryCount: 1,
    repeatedQueryCount: 1,
    slowQueries: ['slow'],
    repeatedQueries: ['repeated'],
    endpointTimings: ['timing'],
    truncated: false,
    ...overrides,
});

test('adds up the counts and lists of every node', () => {
    const merged = mergeSlowQueryReports([report(), report({ slowQueries: ['other'], endpointTimings: [] })]);

    assert.equal(merged.slowQueryCount, 2);
    assert.equal(merged.repeatedQueryCount, 2);
    assert.deepEqual(merged.slowQueries, ['slow', 'other']);
    assert.deepEqual(merged.endpointTimings, ['timing']);
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

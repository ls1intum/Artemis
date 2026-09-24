import { tumUiCn } from './tum-ui-cn';

describe('tumUiCn', () => {
    it('merges package-prefixed utilities without merging host utilities', () => {
        expect(tumUiCn('tum-ui-option', 'tum:px-2', 'tum:px-4', 'px-2')).toBe('tum-ui-option tum:px-4 px-2');
        expect(tumUiCn('tum:text-sm', 'tum:text-primary')).toBe('tum:text-sm tum:text-primary');
        expect(tumUiCn('tum:bg-highlight-background tum:text-text', 'tum:bg-highlight-focus-background tum:text-highlight')).toBe(
            'tum:bg-highlight-focus-background tum:text-highlight',
        );
    });
});

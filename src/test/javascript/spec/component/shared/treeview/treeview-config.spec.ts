import { TreeviewConfig } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-config';

describe('TreeviewConfig', () => {
    it('should have sensible default values', () => {
        const config = new TreeviewConfig();
        expect(config.hasAllCheckBox).toBe(true);
        expect(config.hasFilter).toBe(false);
        expect(config.hasCollapseExpand).toBe(false);
        expect(config.maxHeight).toBe(500);
        expect(config.decoupleChildFromParent).toBe(false);
    });
});

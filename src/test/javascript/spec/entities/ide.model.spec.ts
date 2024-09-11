import { Ide, ideEquals } from 'app/shared/user-settings/ide-preferences/ide.model';

describe('Ide Model Tests', () => {
    it('should create an Ide instance with the given name and deepLink', () => {
        const name = 'VS Code';
        const deepLink = 'vscode://vscode.git/clone?url={cloneUrl}';
        const ide = { name: name, deepLink: deepLink };

        expect(ide.name).toBe(name);
        expect(ide.deepLink).toBe(deepLink);
    });

    it('should return false if either Ide is undefined', () => {
        const ide1: Ide | undefined = undefined;
        const ide2: Ide | undefined = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };

        expect(ideEquals(ide1, ide2)).toBeFalse();
        expect(ideEquals(ide2, ide1)).toBeFalse();
        expect(ideEquals(undefined, undefined)).toBeFalse();
    });

    it('should return false if Ide deepLinks are different', () => {
        const ide1: Ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };
        const ide2: Ide = { name: 'VS Code', deepLink: 'vscode://open?url={cloneUrl}' };

        expect(ideEquals(ide1, ide2)).toBeFalse();
    });

    it('should return true if Ide deepLinks are the same', () => {
        const ide1: Ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };
        const ide2: Ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };

        expect(ideEquals(ide1, ide2)).toBeTrue();
    });
});

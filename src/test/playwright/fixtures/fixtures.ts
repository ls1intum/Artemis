import { promises as fs } from 'fs';

export class Fixtures {
    static async get(filePath: string) {
        try {
            const fullPath = `${__dirname}/${filePath}`;
            return await fs.readFile(fullPath, 'utf-8');
        } catch (error) {
            console.error(`Error reading fixture file: ${error.message}`);
        }
    }

    static getAbsoluteFilePath(filePath: string) {
        return `${__dirname}/${filePath}`;
    }
}

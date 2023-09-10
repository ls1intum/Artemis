export class FileDetails {
    constructor(
        public name: string = '',
        public extension: string = '',
    ) {}
    static getFileDetailsFromPath(filePath: string): FileDetails {
        const filePathSplit: string[] = filePath.split('/');
        const fileName = filePathSplit.last()!;
        const fileNameSplit: string[] = fileName.split('.');
        const fileExtension = fileNameSplit.last()!;

        return new FileDetails(fileName, fileExtension);
    }
}

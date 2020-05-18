export const programmingExerciseProblemStatementC =
    '#### General Tasks\n' +
    '1. [task][Compile](TestCompile)\n' +
    '\n' +
    '#### Adress Sanitizer\n' +
    '1. [task][Compile Address Sanitizer](TestCompileASan)\n' +
    '\n' +
    '#### Undefined Behavior Sanitizer\n' +
    '1. [task][Compile Undefined Behavior Sanitizer](TestCompileUBSan)\n' +
    '\n' +
    '#### Leak Sanitizer\n' +
    '1. [task][Compile Leak Sanitizer](TestCompileLeak)';

export const buildErrorContentC = {
    newFiles: [],
    content: [
        {
            fileName: 'rotX.c',
            fileContent: 'a',
        },
    ],
};

export const someSuccessfulErrorContentC = {
    newFiles: [],
    content: [
        {
            fileName: 'rotX.c',
            fileContent: 'int main(void) {\n' + '\treturn 0; // Success\n' + '}\n',
        },
    ],
};

export const allSuccessfulContentC = {
    newFiles: [],
    content: [
        {
            fileName: 'main.c',
            fileContent:
                '#include <ctype.h> // isalpha(...), isupper(...)\n' +
                '#include <stdlib.h> // size_t\n' +
                '#include <unistd.h> // read(...)\n' +
                '#include <stdio.h> // printf(...)\n' +
                '\n' +
                '#define MAX_BUFFER_SIZE 1024\n' +
                '\n' +
                'char rotX(char in, unsigned rot);\n' +
                'unsigned readRotCount();\n' +
                '\n' +
                'char rotX(char in, unsigned rot) {\n' +
                'if(isalpha(in)) { // We only want to convert alphabet characters\n' +
                'if(isupper(in)) {\n' +
                'return \'A\' + ((in - \'A\') + rot) % 26;\n' +
                '}\n' +
                'return \'a\' + ((in - \'a\') + rot) % 26;\n' +
                '}\n' +
                'return in;\n' +
                '}\n' +
                '\n' +
                'unsigned readRotCount() {\n' +
                'int rot = -1;\n' +
                'do\n' +
                '{   \n' +
                'printf("Enter Rot:\n");\n' +
                'fflush(stdout);\n' +
                'if(!scanf("%i", &rot)) {\n' +
                '// Clear input if user did not enter a valid int:\n' +
                'int c;\n' +
                'while ((c = getchar()) != \'\n\' && c != EOF);\n' +
                '}\n' +
                '} while (rot < 0);\n' +
                'return (unsigned)rot;\n' +
                '}\n' +
                '\n' +
                'int main() {\n' +
                'unsigned rot = readRotCount();\n' +
                'char buff[MAX_BUFFER_SIZE];\n' +
                '\n' +
                'printf("Enter text:\n");\n' +
                '// Read MAX_BUFFER_SIZE - 1 chars. Don\'t forget about the \'\0\' at the end!\n' +
                'size_t n = read(STDIN_FILENO, buff, MAX_BUFFER_SIZE - 1);\n' +
                'for (size_t i = 0; i < n && buff[i]; i++)\n' +
                '{\n' +
                '// Replace character by character:\n' +
                'buff[i] = rotX(buff[i], rot);\n' +
                '}\n' +
                '// Print the result:\n' +
                'printf("%s", buff);\n' +
                '\n' +
                '}',
        },
    ],
};

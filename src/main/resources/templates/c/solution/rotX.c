#include <ctype.h> // isalpha(...), isupper(...)
#include <stdlib.h> // size_t
#include <unistd.h> // read(...)
#include <stdio.h> // printf(...)

#define MAX_BUFFER_SIZE 1024

char rotX(char in, unsigned rot);
unsigned readRotCount();

char rotX(char in, unsigned rot) {
    if(isalpha(in)) { // We only want to convert alphabet characters
        if(isupper(in)) {
            return 'A' + ((in - 'A') + rot) % 26;
        }
        return 'a' + ((in - 'a') + rot) % 26;
    }
    return in;
}

unsigned readRotCount() {
    int rot = -1;
    do
    {   
        printf("Enter Rot:\n");
        fflush(stdout);
        if(!scanf("%i", &rot)) {
            // Clear input if user did not enter a valid int:
            int c;
            while ((c = getchar()) != '\n' && c != EOF);
        }
    } while (rot < 0);
    return (unsigned)rot;
}

int main() {
    unsigned rot = readRotCount();
    char buff[MAX_BUFFER_SIZE];

    printf("Enter text:\n");
    // Read MAX_BUFFER_SIZE - 1 chars. Don't forget about the '\0' at the end!
    size_t n = read(STDIN_FILENO, buff, MAX_BUFFER_SIZE - 1);
    for (size_t i = 0; i < n && buff[i]; i++)
    {
        // Replace character by character:
        buff[i] = rotX(buff[i], rot);
    }
    // Print the result:
    printf("%s", buff);
    
}
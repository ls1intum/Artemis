/**
 * Shadows all exec commands if compiled with
 **/

#include <signal.h>
#include <stdio.h>
#include <sys/types.h>

void printWarning() {
    printf("Access denied! This will be reported.\n");
}

int system(const char* command) {
    // Since we do not use the data attribute this surpresses the
    // "warning: unused parameter ..." during compile time
    (void) command;

    printWarning();
    return -1;
}

int execl(const char* pathname, const char* arg, ...) {
    // Since we do not use the data attribute this surpresses the
    // "warning: unused parameter ..." during compile time
    (void) pathname;
    (void) arg;

    printWarning();
    return -1;
}

int execlp(const char* file, const char* arg, ...) {
    // Since we do not use the data attribute this surpresses the
    // "warning: unused parameter ..." during compile time
    (void) file;
    (void) arg;

    printWarning();
    return -1;
}

int execle(const char* pathname, const char* arg, ...) {
    // Since we do not use the data attribute this surpresses the
    // "warning: unused parameter ..." during compile time
    (void) pathname;
    (void) arg;

    printWarning();
    return -1;
}

int execv(const char* pathname, char* const argv[]) {
    // Since we do not use the data attribute this surpresses the
    // "warning: unused parameter ..." during compile time
    (void) pathname;
    (void) argv;

    printWarning();
    return -1;
}

int execve(const char* path, char* const argv[], char* const envp[]) {
    // Since we do not use the data attribute this surpresses the
    // "warning: unused parameter ..." during compile time
    (void) path;
    (void) argv;
    (void) envp;

    printWarning();
    return -1;
}

int execvp(const char* file, char* const argv[]) {
    // Since we do not use the data attribute this surpresses the
    // "warning: unused parameter ..." during compile time
    (void) file;
    (void) argv;

    printWarning();
    return -1;
}

int execvpe(const char* file, char* const argv[], char* const envp[]) {
    // Since we do not use the data attribute this surpresses the
    // "warning: unused parameter ..." during compile time
    (void) file;
    (void) argv;
    (void) envp;

    printWarning();
    return -1;
}

int fexecve(int fd, char* const argv[], char* const envp[]) {
    // Since we do not use the data attribute this surpresses the
    // "warning: unused parameter ..." during compile time
    (void) fd;
    (void) argv;
    (void) envp;

    printWarning();
    return -1;
}

int execveat(int dirfd, const char* pathname, char* const argv[], char* const envp[], int flags) {
    // Since we do not use the data attribute this surpresses the
    // "warning: unused parameter ..." during compile time
    (void) dirfd;
    (void) pathname;
    (void) argv;
    (void) envp;
    (void) flags;

    printWarning();
    return -1;
}

int kill(pid_t pid, int sig) {
    if (pid == 0 || pid == -1) {
        printf("You're not allowed to send signals to PID %i! This will be reported.\n", pid);
        return -1;
    }
    return kill(pid, sig);
}

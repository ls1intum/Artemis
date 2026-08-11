/**
 * Shadows all exec commands if compiled with
 **/

// Needed so <unistd.h> declares syscall(2) under -std=c11, which kill() below uses to reach the
// real implementation. Defined before any include, and only in this file, so the student's own
// translation unit is unaffected.
#define _GNU_SOURCE

#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>

// TODO Overloading malloc seems to cause problems with asan:
// ==25708==AddressSanitizer CHECK failed: ../../../../src/libsanitizer/asan/asan_posix.cc:50 "((tsd_key_inited)) != (0)" (0x0, 0x0)
//     <empty stack>
#if !defined(__SANITIZE_ADDRESS__) && !defined(GBS_DISABLE_MALLOC_SHADOW)
// Replace the regular malloc call and fill
// the returning pointer with random garbage data
extern void* __libc_malloc(size_t size);

void* malloc(size_t size) {
    void* ptr = __libc_malloc(size);
    if (ptr != NULL) {
        int urandom = open("/dev/urandom", O_RDONLY);
        if (urandom != -1) {
            read(urandom, ptr, size);
            close(urandom);
        }
    }
    return ptr;
}
#endif

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
    // Only a signal to a single, named process gets through. Every pid <= 0 addresses more than one
    // process: 0 is the caller's own process group, -1 is every process the user may signal, and any
    // other negative pid is the process group -pid.
    if (pid <= 0) {
        printf("You're not allowed to send signals to PID %i! This will be reported.\n", pid);
        return -1;
    }
    // Issue the syscall directly. Calling kill() here re-enters this shadow -- it is the only one
    // that passes anything through, so it is the only one that can recurse -- and the recursion ran
    // until the stack overflowed. A targeted signal therefore never reached the kernel: the program
    // died instead, and under AddressSanitizer it died as a bare "AddressSanitizer:DEADLYSIGNAL".
    return (int) syscall(SYS_kill, pid, sig);
}

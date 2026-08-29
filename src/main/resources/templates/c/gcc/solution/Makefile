.PHONY: all run

all: helloWorld

#############################################################
# DON'T change the variable names of INCLUDEDIRS and SOURCE
#############################################################
# A list of include directories
INCLUDEDIRS =
# A list of source files
SOURCE = helloWorld.c

ifeq ($(origin CC),default)
CC = gcc
endif

# Warning flags
# For more information about gcc warnings: https://embeddedartistry.com/blog/2017/3/7/clang-weverything
# -Wall:		        Print warnings
# -Wextra:		        Enable additional warnings not covered by "-Wall"
# -Wpedantic:	        Reject everything that is not ISO C
# -g					Generates debug information to be used by GDB debugger
WFLAGS = -Wall -Wextra -Wpedantic -g

# Flags that only the sanitizer targets (asan, ubsan, lsan) receive.
# -fno-pie -no-pie  Link at a fixed low load address instead of a randomised one. GCC's address and
#                   leak sanitizer runtimes reserve a fixed region at 0x600000000000 (96 TiB) for
#                   their allocator, and a position-independent executable can be loaded inside it
#                   on a host with vm.mmap_rnd_bits=32 (the kernel default since 6.5, so Ubuntu
#                   24.04 among others). The sanitizer then dies before main() in an endless loop of
#                   "AddressSanitizer:DEADLYSIGNAL" lines and never exits. A non-PIE executable is
#                   loaded at 0x400000 and cannot collide. libsanitizer made the allocator base
#                   dynamic in GCC 14, so these two flags are unnecessary with that or newer.
#                   See https://github.com/google/sanitizers/issues/1614
SANITIZER_FLAGS = -fno-pie -no-pie


# Compile without sanitizers and disable optimisation
# $(SOURCE): 	        Input file(s)
# $(INCLUDEDIRS:%=-I%)  Include directories
# -o: 			        Link the resulting object files
# $@.out:	            Built-in variable representing the current target name + file extension .out
# -std=c11              Set C standard
# -O0:			        Do not optimize the program
# $(WFLAGS)             Warning flags
helloWorld: FORCE
	$(CC) $(SOURCE) $(INCLUDEDIRS:%=-I%) -o $@.out -std=c11 -O0 $(WFLAGS)

# Compile with address sanitizer enabled
# $(SOURCE): 			Input file(s)
# $(INCLUDEDIRS:%=-I%)  Include directories
# -o: 					Link the resulting object files
# $@.out:	            Built-in variable representing the current target name + file extension .out
# -std=c11              Set C standard
# -O0:					Do not optimize the program
# $(WFLAGS)             Warning flags
# -Werror:				Treat all warnings as errors
# -fsanitize=address:	Address sanitizer
#                       (https://gcc.gnu.org/onlinedocs/gcc-5.3.0/gcc/Debugging-Options.html#index-fsanitize_003dundefined-652)
asan: FORCE
	$(CC) $(SOURCE) $(INCLUDEDIRS:%=-I%) -o $@.out -std=c11 -O0 $(WFLAGS) $(SANITIZER_FLAGS) -Werror -fsanitize=address

# Compile with undefined behavior sanitizer enabled
# $(SOURCE): 			Input file(s)
# $(INCLUDEDIRS:%=-I%)  Include directories
# -o: 					Link the resulting object files
# $@.out:	            Built-in variable representing the current target name + file extension .out
# -std=c11              Set C standard
# -O0:					Do not optimize the program
# $(WFLAGS)             Warning flags
# -Werror:				Treat all warnings as errors
# -fsanitize=undefined:	Fast undefined behavior check
#                       (https://gcc.gnu.org/onlinedocs/gcc-5.3.0/gcc/Debugging-Options.html#index-fsanitize_003dundefined-652)
ubsan: FORCE
	$(CC) $(SOURCE) $(INCLUDEDIRS:%=-I%) -o $@.out -std=c11 -O0 $(WFLAGS) $(SANITIZER_FLAGS) -Werror -fsanitize=undefined

# Compile with leak sanitizer enabled
# $(SOURCE): 		    Input file(s)
# $(INCLUDEDIRS:%=-I%)  Include directories
# -o: 				    Link the resulting object files
# $@.out:	            Built-in variable representing the current target name + file extension .out
# -std=c11              Set C standard
# -O0:				    Do not optimize the program
# $(WFLAGS)             Warning flags
# -Werror:			    Treat all warnings as errors
# -fsanitize=leak	    Basic memory leak sanitizer
#                       (https://gcc.gnu.org/onlinedocs/gcc-5.3.0/gcc/Debugging-Options.html#index-fsanitize_003dundefined-652)
lsan: FORCE
	$(CC) $(SOURCE) $(INCLUDEDIRS:%=-I%) -o $@.out -std=c11 -O0 $(WFLAGS) $(SANITIZER_FLAGS) -Werror -fsanitize=leak

# Compile with GCC static analysis enabled
# $(SOURCE): 			Input file(s)
# $(INCLUDEDIRS:%=-I%)  Include directories
# -o: 					Link the resulting object files
# $@.out:	            Built-in variable representing the current target name + file extension .out
# -std=c11              Set C standard
# -O0:					Do not optimize the program
# $(WFLAGS)             Warning flags
# -fanalyzer:			GCC static analysis (for GCC >= 10.0 only)
#                       (https://developers.redhat.com/blog/2020/03/26/static-analysis-in-gcc-10/)
staticAnalysis: FORCE
	$(CC) $(SOURCE) $(INCLUDEDIRS:%=-I%) -o $@.out -std=c11 -O0 $(WFLAGS) -fanalyzer

# Execute the compiled programm
run:
	./helloWorld.out

# Make sure we always rebuild
# Required for the tester
FORCE: ;

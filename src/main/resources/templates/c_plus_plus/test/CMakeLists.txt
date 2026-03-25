cmake_minimum_required(VERSION 3.13)
project(ArtemisTest)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(CTest)

find_package(Catch2 3.0 REQUIRED)

add_subdirectory("${studentParentWorkingDirectoryName}")

cmake_minimum_required(VERSION 3.13)
project(ArtemisTest)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(CTest)

find_package(Catch2 REQUIRED)

add_subdirectory("${studentParentWorkingDirectoryName}")

add_executable(sort-test src/sort-test.cpp)
target_link_libraries(sort-test assignment Catch2::Catch2WithMain)
add_test(NAME sort-test COMMAND sort-test)

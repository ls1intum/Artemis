#include "sort.hpp"

#include <algorithm>
#include <random>
#include <utility>
#include <vector>

#include <catch2/catch_test_macros.hpp>

void run_all_algorithms(std::vector<int>& values,
                        const std::vector<int>& expected) {
  SECTION("selection_sort") {
    selection_sort(values.begin(), values.end());
    REQUIRE(values == expected);
  }
  SECTION("insertion_sort") {
    insertion_sort(values.begin(), values.end());
    REQUIRE(values == expected);
  }
  SECTION("quicksort") {
    quicksort(values.begin(), values.end());
    REQUIRE(values == expected);
  }
  SECTION("mergesort") {
    mergesort(values.begin(), values.end());
    REQUIRE(values == expected);
  }
  SECTION("mergesort_inplace") {
    mergesort_inplace(values.begin(), values.end());
    REQUIRE(values == expected);
  }
  SECTION("heapsort") {
    heapsort(values.begin(), values.end());
    REQUIRE(values == expected);
  }
  SECTION("heapsort_explicit") {
    heapsort_explicit(values.begin(), values.end());
    REQUIRE(values == expected);
  }
}

TEST_CASE("sorting_algorithms") {
  std::vector<int> values{6, 2, 4, 2, 1, 7, 0, 2, 3, 4, 8};
  std::vector<int> expected{0, 1, 2, 2, 2, 3, 4, 4, 6, 7, 8};

  run_all_algorithms(values, expected);
}

TEST_CASE("sorting_algorithms/all_elements_equal") {
  std::vector<int> values(20, 1);
  auto expected = values;

  // just to make sure your code doesn't crash on repeated values
  run_all_algorithms(values, expected);
}

TEST_CASE("sorting_algorithms/reverse-sorted_values") {
  std::vector<int> values(20, 1);
  std::iota(values.begin(), values.end(), 0);
  auto expected = values;
  std::reverse(values.begin(), values.end());

  run_all_algorithms(values, expected);
}

TEST_CASE("sorting_algorithms/single_values") {
  std::vector<int> values{4};
  std::vector<int> expected{4};

  // just to make sure your code doesn't crash on single values
  run_all_algorithms(values, expected);
}

TEST_CASE("sorting_algorithms/empty_input") {
  std::vector<int> values;
  std::vector<int> expected;

  // just to make sure your code doesn't crash on empty inputs
  run_all_algorithms(values, expected);
}

TEST_CASE("sorting_algorithms/large_input") {
  std::vector<int> values;
  std::uniform_int_distribution<int> dist{0, 50};
  std::default_random_engine rng;  // default seed
  for (int i = 0; i < 100; ++i) {
    values.push_back(dist(rng));
  }
  auto expected = values;
  std::sort(expected.begin(), expected.end());

  run_all_algorithms(values, expected);
}

TEST_CASE("bogosort") {
  // bogosort only works for very small inputs,
  // large inputs take forever
  std::vector<int> values{6, 2, 4, 2};
  std::vector<int> expected{2, 2, 4, 6};

  bogosort(values.begin(), values.end());

  REQUIRE(values == expected);
}

TEST_CASE("bogosort/empty_input") {
  std::vector<int> values{};
  std::vector<int> expected{};

  bogosort(values.begin(), values.end());

  REQUIRE(values == expected);
}

TEST_CASE("bogosort/single_value") {
  std::vector<int> values{3};
  std::vector<int> expected{3};

  bogosort(values.begin(), values.end());

  REQUIRE(values == expected);
}

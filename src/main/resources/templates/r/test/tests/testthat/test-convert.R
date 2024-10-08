test_that("converts_3x3_matrix_to_vectors", {
  mat <- matrix(c(5, 8, 11, 6, 9, 12, 7, 10, 13), nrow = 3, ncol = 3)

  result <- assignment::matrix_to_column_list(mat)

  # Make sure to only use exactly one "expect_" function per test
  expect_equal(result, list(
    c(5, 8, 11),
    c(6, 9, 12),
    c(7, 10, 13)
  ))
})

test_that("converts_4x2_matrix_to_vectors", {
  mat <- matrix(c(13, 13, 5, 18, 11, 4, 7, 10), nrow = 4, ncol = 2)

  result <- assignment::matrix_to_column_list(mat)

  expect_equal(result, list(
    c(13, 13, 5, 18),
    c(11, 4, 7, 10)
  ))
})

test_that("converts_1x5_matrix_to_scalars", {
  mat <- matrix(c(16, 10, 15, 8, 7), nrow = 1, ncol = 5)

  result <- assignment::matrix_to_column_list(mat)

  expect_equal(result, list(
    16,
    10,
    15,
    8,
    7
  ))
})

test_that("converts_5x1_matrix_to_vector", {
  mat <- matrix(c(14, 9, 1, 3, 4), nrow = 5, ncol = 1)

  result <- assignment::matrix_to_column_list(mat)

  expect_equal(result, list(
    c(14, 9, 1, 3, 4)
  ))
})

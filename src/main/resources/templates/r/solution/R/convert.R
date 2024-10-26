matrix_to_column_list <- function(mat) {
  if (!is.matrix(mat)) {
    stop("Input must be a matrix")
  }

  n_cols <- ncol(mat)

  # Initialize an empty list to store column-vectors
  column_list <- vector("list", length = n_cols)

  # Loop through each column and store it in the list
  for (i in 1:n_cols) {
    column_list[[i]] <- mat[, i]
  }

  return(column_list)
}

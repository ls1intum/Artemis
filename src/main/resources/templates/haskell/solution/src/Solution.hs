module Solution where

-- Implement the fibonacci numbers function with initial values fib 0 = 0 and fib 1 = 1
fib :: Integer -> Integer
fib 0 = 0
fib 1 = 1
fib n | 1 < n = fib (n - 1) + fib (n - 2)

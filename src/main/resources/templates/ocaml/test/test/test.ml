open OUnit2
open OUnitTest
open Assignment

(** Default timeout per test-case, in seconds. *)
let default_timeout = Custom_length 2.

(** Create test case with default timeout *)
let test_case (f: test_fun) : test = TestCase (default_timeout, f)

(** Create test case with default timeout and the specified description *)
let test_case_d (descr: string) (f: test_fun) = descr >: (test_case f)


(* Tests *)

let add_tests = "add tests" >::: [
  (* Test add, use string_of_int for output in failure case *)
  test_case_d "add 0 0" (fun _ -> assert_equal 0 (add 0 0) ~printer:string_of_int);
  test_case_d "add 2 1" (fun _ -> assert_equal 3 (add 2 1) ~printer:string_of_int);
]

let filter_test = test_case_d "filter even [1;2;3;4]" (fun _ -> assert_equal [2;4] (filter (fun i -> i mod 2 = 0) [1;2;3;4]))

let starts_with_tests = "starts_with tests" >:::
  [test_case_d "true case" (fun _ -> assert_bool "starts_with should return true" (starts_with "hallo" "hal"));
  test_case_d "false case"  (fun _ -> assert_bool "starts_with should return false" (not (starts_with "hallo" "allo")))]

let starts_with2_tests = "starts_with2 tests" >:::
  [test_case_d "true case" (fun _ -> assert_bool "starts_with2 should return true" (starts_with2 "hallo" "hal"));
  test_case_d "false case" (fun _ -> assert_bool "starts_with2 should return false" (not (starts_with2 "hallo" "allo")))]

(* List tests that should be executed *)
let tests = TestList [add_tests; filter_test; starts_with_tests; starts_with2_tests]
let _ =
  run_test_tt_main tests

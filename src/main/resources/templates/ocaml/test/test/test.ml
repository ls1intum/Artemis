open OUnit2
open Assignment

let even i = i mod 2 == 0

let tests = "test suite for sum" >::: [
  (* Test add, use string_of_int for output in failur case *)
  "add 0 0"  >:: (fun _ -> assert_equal 0 (add 0 0) ~printer:string_of_int);
  "add 2 1"    >:: (fun _ -> assert_equal 3 (add 2 1) ~printer:string_of_int);
  "filter even [1;2]" >:: (fun _ -> assert_equal [2] (filter even [1;2]));
  "starts_with true" >:: (fun _ -> assert_bool "starts_with should return true" (starts_with "hallo" "hal"));
  "starts_with false" >:: (fun _ -> assert_bool "starts_with should return false" (not (starts_with "hallo" "allo")));
  "starts_with2 true " >:: (fun _ -> assert_bool "starts_with2 should return true" (starts_with2 "hallo" "hal"));
  "starts_with2 false" >:: (fun _ -> assert_bool "starts_with2 should return false" (not (starts_with2 "hallo" "allo")));
]

let _ =
  run_test_tt_main tests

open OUnit2
open OUnitTest

(** Default timeout per test-case, in seconds. *)
let default_timeout = Custom_length 2.

(** Default timeout for qcheck tests, in seconds. *)
let qcheck_timeout = Custom_length 30.

(** Create test case with default timeout *)
let test_case (f : test_fun) : test = TestCase (default_timeout, f)

(** Create test case with default timeout and the specified description *)
let test_case_d (descr : string) (f : test_fun) = descr >: test_case f

let hiddenFail = test_case (fun _ -> assert_failure "This Test is hidden")

(** Mark a test as hidden and ensure it only runs after  *)
let rec hidden = function
  | TestCase (dur, func) ->
      if RunHidden.runHidden then TestCase (dur, func) else hiddenFail
  | TestList tests -> TestList (List.map hidden tests)
  | TestLabel (name, inner) -> TestLabel (name ^ " [hidden]", hidden inner)

let to_ounit qc =
  match QCheck_runner.to_ounit2_test qc with
  | TestLabel (name, TestCase (_, func)) ->
      TestLabel (name, TestCase (qcheck_timeout, func))
  | _ -> failwith "failed to convert qcheck"

(* Tests *)

(* helper function *)
let even i = i mod 2 = 0

let add_tests =
  "add tests"
  >::: [
         (* Test add, use string_of_int for output in failure case *)
         test_case_d "add 0 0" (fun _ ->
             assert_equal (Solution.add 0 0) (Assignment.add 0 0)
               ~printer:string_of_int);
         test_case_d "add 2 1" (fun _ ->
             assert_equal (Solution.add 2 1) (Assignment.add 2 1)
               ~printer:string_of_int);
       ]

let filter_test =
  test_case_d "filter even [1;2;3;4]" (fun _ ->
      assert_equal [ 2; 4 ] (Assignment.filter even [ 1; 2; 3; 4 ]))

let starts_with_tests =
  "starts_with tests"
  >::: [
         test_case_d "true case" (fun _ ->
             assert_bool "starts_with should return true"
               (Assignment.starts_with "hallo" "hal"));
         test_case_d "false case" (fun _ ->
             assert_bool "starts_with should return false"
               (not (Assignment.starts_with "hallo" "allo")));
       ]

let starts_with2_tests =
  "starts_with2 tests"
  >::: [
         test_case_d "true case" (fun _ ->
             assert_bool "starts_with2 should return true"
               (Assignment.starts_with2 "hallo" "hal"));
         test_case_d "false case" (fun _ ->
             assert_bool "starts_with2 should return false"
               (not (Assignment.starts_with2 "hallo" "allo")));
       ]

(* QCheck after due tests *)
let filter_prop =
  QCheck.Test.make ~count:10000 ~name:"filter prop"
    QCheck.(list small_nat)
    (fun l -> Assignment.filter even l = Solution.filter even l)
  |> to_ounit |> hidden

(* List tests that should be executed *)
let tests =
  TestList
    [
      add_tests; filter_test; starts_with_tests; starts_with2_tests; filter_prop;
    ]

let _ = run_test_tt_main tests

(** main tests file

    This template is set up to test submissions in public tests with a few
    hand-picked inputs and then to use qcheck for comprehensive tests after the
    submission deadline.

    if some things here appear to be somewhat complicated, it is most likely
    to reuse as much code as possible between public and hidden tests and to
    give the same feedback for both.

    If you do not want to use hidden tests, simply remove all "|> hidden"
    If you use hidden tests you must adjust the exercise working time in run.sh

    It is of course possible to use ounit directly but it should generally not
    be necessary with this framework
 *)

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

let hiddenFail = test_case (fun _ -> Printexc.record_backtrace false; assert_failure "This Test is hidden")

(** Mark a test as hidden and ensure it only runs after  *)
let rec hidden = function
  | TestCase (dur, func) ->
      if RunHidden.runHidden then TestCase (dur, func) else hiddenFail
  | TestList tests -> TestList (List.map hidden tests)
  | TestLabel (name, inner) -> TestLabel (name ^ " [hidden]", hidden inner)

(** convert a qcheck test to ounit, overwriting the timeout *)
let to_ounit qc =
  match QCheck_runner.to_ounit2_test qc with
  | TestLabel (name, TestCase (_, func)) -> TestLabel (name, TestCase (qcheck_timeout, func))
  | _ -> failwith "failed to convert qcheck"

(** convert a qcheck test to ounit, overwriting the timeout to be short and removing the name *)
let to_ounit_single qc =
  match QCheck_runner.to_ounit2_test qc with
  | TestLabel (_, TestCase (_, func)) -> TestCase (default_timeout, func)
  | _ -> failwith "failed to convert qcheck"

(** join a test tree into a single test that passes only, if all individual tests in the tree pass
    students will only be able to see the result of the first test that failed
    this somewhat changes the timeout behavior of the tests
    workaround for limitations in artemis' grading system
 *)
let joinTests test =
  let rec collect (timeout, tests) = function
    | TestList list -> List.fold_left collect (timeout, tests) list
    | TestLabel (_, inner) -> collect (timeout, tests) inner
    | TestCase (t, f) -> (timeout +. delay_of_length t, f :: tests)
  in let (t, f) = collect (0.0, []) test in
  TestCase (Custom_length t, fun ctx -> List.iter (( |> ) ctx) f)

(** execute func and capture any exceptions *)
let handle_exn func =
  Printexc.record_backtrace false; (* change to true to attempt to show a backtrace to students; note that it is mostly useless *)
  let result =
    try 
      Ok (func ())
    with
    | e -> Error (Printexc.get_backtrace (), e)
  in
  Printexc.record_backtrace false;
  result

(** check that two values wrapped in a (fun _ -> x) are equals,
    if not prints expected/actual or any exceptions for qcheck
    values are wrapped to be able to handle exceptions here
 *)
let assert_equal ?(cmp = ( = )) ?printer expected_f actual_f =
  match handle_exn expected_f, handle_exn actual_f with
  | Ok expected, Ok actual ->
    let get_error_string () =
      match printer with
        | Some p -> Printf.sprintf "Expected:\n%s\nBut got:\n%s"
              (p expected) (p actual)
        | None -> "not equal"
    in
    if not (cmp expected actual) then
      QCheck.Test.fail_report (get_error_string ())
    else
      true
  | Error _, _ -> QCheck.Test.fail_report "The solution caused an error, please report this to an instructor"
  | _, Error (bt, e) ->
    let msg =
      Printf.sprintf "Your submission raised an error %s\n%s" (Printexc.to_string e) bt
    in
      QCheck.Test.fail_report msg

(** reads a whole file from disk as a string *)
let read_file_whole fn =
  let ch = open_in_bin fn in
  let s = really_input_string ch (in_channel_length ch) in
  close_in ch;
  s

(** post process the test result file as artemis will use too much of it otherwise *)
let prettify_results fn =
  let data = read_file_whole fn in
  let data = Str.global_substitute (Str.regexp "<failure[^>]+?>\n?") (Fun.const "<failure>") data in
  let data = Str.global_substitute (Str.regexp "\n*No backtrace.</failure>") (Fun.const "</failure>") data in
  let oc = open_out fn in
  output_string oc data;
  close_out oc


(* print things as strings *)
let string_of_list string_of_element input_list =
  let add a b = a ^ "; " ^ string_of_element b in
  match input_list with
  | [] -> "[]"
  | h :: t -> "[" ^ List.fold_left add (string_of_element h) t ^ "]"

let string_of_tuple string_of_a string_of_b (a,b) = "(" ^ string_of_a a ^ ", " ^ string_of_b b ^ ")"



(* arbitraries *)

(* generate, print and shrink arguments for various functions to test *)
(* it is generally a good idea to use small_ versions or otherwise limit the size of inputs,
   as otherwise tests will take forever and interesting collisions unlikely *)
let add_arb = QCheck.(pair small_int small_int)
let filter_arb = QCheck.(pair (fun1_unsafe small_int bool) (small_list small_int))
let filter_arb_even = QCheck.(pair (fun1_unsafe small_int bool |> QCheck.set_print (Fun.const "even")) (small_list small_int)) (* can't print non-generated functions *)
let starts_with_arb = QCheck.(pair small_string small_string)



(* Tests *)

(** create a qcheck test that runs chk with the argument arg (and only that argument)
    name: name to show to students, even when show test names is disabled
    chk: function that checks if the student submission is correct for a given input
    arb: an arbitrary of the correct type for chk, only the printer of the arbitrary is used
    arg: the argument to pass to chk
 *)
let make_test name chk arb arg = QCheck.Test.make ~name ~count:1 QCheck.(map_same_type (Fun.const arg) arb |> QCheck.set_shrink (Fun.const QCheck.Iter.empty)) chk |> to_ounit_single


(** a function that checks if the students submission if correct for some input (use tuples if the functions has more than one argument) 
    usually it is enough to just call assert_equal like this
    the printer argument is for the result type of the tested function
    if there is more than one possible result, it is usually possible to provide a custom comparison function cmp that takes care of the differences
    if not, you can wrap the function result in some other type, to provide the comparison function with enough data to determine if the result is valid
    or you could just drop assert_equal entirely, but it takes care of a bunch of things to make things pretty for students
*)
let add_chk (a, b) =
  assert_equal
    (fun _ -> Solution.add a b)
    (fun _ -> Assignment.add a b)
    ~printer:string_of_int

(** just an utility to easily create tests from add_chk with a single input *)
let add_test a b = make_test "add" add_chk add_arb (a, b)

(* repeat for the other functions to test *)
let filter_chk (f, list) =
  assert_equal
    (fun _ -> Solution.filter f list)
    (fun _ -> Assignment.filter f list)
    ~printer:(string_of_list string_of_int)
let filter_test f list = make_test "filter" filter_chk filter_arb_even (f, list)

let starts_with_chk (a, b) =
  assert_equal
    (fun _ -> Solution.starts_with a b)
    (fun _ -> Assignment.starts_with a b)
    ~printer:string_of_bool
let starts_with_test f list = make_test "starts_with" starts_with_chk starts_with_arb (f, list)



(** tests with a given input *)
let add_tests = "add" >::: [
    add_test 0 0;
    add_test 2 1;
  ]

(** and more tests with randomized inputs *)
let add_prop = QCheck.Test.make ~count:10000 ~name:"add" add_arb add_chk |> to_ounit |> hidden (* the '|> hidden' causes this test to not run during submission time *)


(** and again repeat for the other functions *)
let even i = i mod 2 = 0
let filter_tests = "filter" >::: [
    filter_test even [];
    filter_test even [1; 2; 3; 4];
  ]
let filter_prop = QCheck.Test.make ~count:10000 ~name:"filter" filter_arb filter_chk |> to_ounit |> hidden

let starts_with_tests = "starts_with" >::: [
    starts_with_test "hallo" "hal";
    starts_with_test "hallo" "allo";
  ]
let starts_with_prop = QCheck.Test.make ~count:10000 ~name:"starts_with" starts_with_arb starts_with_chk |> to_ounit |> hidden



(* List tests that should be executed *)
let tests =
  TestList
    [
      add_tests;
      add_prop;
      filter_tests;
      filter_prop;
      starts_with_tests;
      starts_with_prop;
    ]

(* only run tests when arguments are passed,
   to be able to check for top-level infinite loops in student code *)
let _ =
  if Array.length Sys.argv > 1 then run_test_tt_main ~exit:(Fun.const ()) tests;
  prettify_results "test-reports/results.xml"

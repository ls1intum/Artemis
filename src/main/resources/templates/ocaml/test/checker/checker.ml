(** Ast checker / sanitizer

    tool to enforce restrictions on what syntax elements are allowed in student code.
    by default restricts use of classes and imperative elements

    - Class declarations
    - Class type declarations
    - Class method calls
    - new class expressions
    - setting class and record fields
    - array literals
    - while loops
    - for loops
    - declaring records with mutable entries
    - external declarations

    external is forbidden as it can be used to circumvent restrictions in the stdlib replacement
    the sequence operator is not forbidden as there is no point,
    "a; b" can be trivially replaced by "let _ = a in b"

    to disable this, simply delete the entire contents of this file
 *)

open Parsetree

(** has there been a violation in the student code? *)
let violation = ref false

let defaultMsg =
  "You may not use the object oriented and imperative subset of ocaml"

(** report a violation in the student code at loc *)
let error_msg ?(msg = defaultMsg) loc =
  let v = Location.msg ~loc "%s" msg in
  let report = Location.{ kind = Report_error; main = v; sub = [] } in
  Location.print_report Format.err_formatter report

(** this ast element is forbidden, if filter returns true for it
    default: the default iterator for this element, to use when the element is not forbidden
    filter: filter to use if only some subtype of this element are forbidden
    getloc: extract the location of an element
    iter, elem: part of the iterator type, do not pass
 *)
let forbidden default ?(filter = fun _ -> true) getloc iter elem =
  if filter elem then (
    getloc elem |> error_msg;
    violation := true)
  else default iter elem

(* checking the ast of a file for forbidden syntax elements *)

let expr_filter = function
  | { pexp_desc = Pexp_send _; _ } -> true
  | { pexp_desc = Pexp_new _; _ } -> true
  | { pexp_desc = Pexp_setinstvar _; _ } -> true
  | { pexp_desc = Pexp_setfield _; _ } -> true
  | { pexp_desc = Pexp_array _; _ } -> true
  | { pexp_desc = Pexp_while _; _ } -> true
  | { pexp_desc = Pexp_for _; _ } -> true
  | _ -> false

let decl_filter = function
  | { ptype_kind = Ptype_record entries; _ } ->
      List.exists
        (function
          | { pld_mutable = Mutable; pld_loc = loc; _ } ->
              error_msg loc;
              false
          | _ -> false)
        entries
  | _ -> false

let struct_filter = function
  | { pstr_desc = Pstr_primitive _; _ } -> true
  | _ -> false

let iter =
  {
    Ast_iterator.default_iterator with
    class_declaration = forbidden Ast_iterator.default_iterator.class_declaration (function { pci_loc = p; _ } -> p);
    class_type_declaration = forbidden Ast_iterator.default_iterator.class_type_declaration (function { pci_loc = p; _ } -> p);
    expr = forbidden Ast_iterator.default_iterator.expr ~filter:expr_filter (function { pexp_loc = p; _ } -> p);
    type_declaration = forbidden Ast_iterator.default_iterator.type_declaration ~filter:decl_filter (function { ptype_loc = p; _ } -> p);
    structure_item = forbidden Ast_iterator.default_iterator.structure_item ~filter:struct_filter (function { pstr_loc = p; _ } -> p);
  }

(** check a single file for violations *)
let checkFile fn =
  try
    (* let _ = print_endline ("Checking: " ^ fn) in *)
    let ast = Pparse.parse_implementation ~tool_name:"" fn in
    (* Printast.structure 0 Format.std_formatter ast;
       Format.fprintf Format.std_formatter "\n"; *)
    iter.structure iter ast
  with exn ->
    violation := true;
    Location.report_exception Format.err_formatter exn

let studentDir = "${studentParentWorkingDirectoryName}"

(** check all student files for violations *)
let _ =
  Sys.readdir studentDir |> Array.to_list
  |> List.filter (fun name -> Filename.check_suffix name ".ml")
  |> List.map (Filename.concat studentDir)
  |> List.iter checkFile;
  exit (if !violation then 1 else 0)

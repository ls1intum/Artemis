(**
  Stdlib replacement / Mock hook for tests

  Student (and solution) code is not linked against the stdlib.
  Instead this module is opened by default like Stdlib is normally.
  If you don't define something in here it will not be available to students.

  Replace the content with
  ```
  module Stdlib = Stdlib
  include Stdlib
  ```
  to disable this and give access to the whole stdlib

  if something is defined as external it must be defined that way here as well,
  as some magic will break otherwise (like && will become eager is you alias it)
  You must use checker to prohibit external (Pstr_primitive) or students
  will be able to redefine things

  this module contains the stdlib for ocaml 4.13.0, with IO, exit, ref, array
  and some internal things commented, so that they are unavailable

  It is of course possible to give functions any definition here,
  making to easy to mock things for io testing
  No mocking facilities are included in this version of the template

  Note that removing things from the stdlib here can cause compile error on
  artemis that does not happen locally.
  Even though the message should be clear enough (Error: unbound value xxxx)
  it may be necessary to document this possibility for students
*)


(******************)
(* Stdlib content *)
(******************)

module OVERRIDE__STDLIB = struct
  external raise : exn -> 'a = "%raise"
  external raise_notrace : exn -> 'a = "%raise_notrace"
  let invalid_arg = Stdlib.invalid_arg
  let failwith = Stdlib.failwith
  exception Exit = Stdlib.Exit
  exception Match_failure = Stdlib.Match_failure
  exception Assert_failure = Stdlib.Assert_failure
  exception Invalid_argument = Stdlib.Invalid_argument
  exception Failure = Stdlib.Failure
  exception Not_found = Stdlib.Not_found
  exception Out_of_memory = Stdlib.Out_of_memory
  exception Stack_overflow = Stdlib.Stack_overflow
  exception Sys_error = Stdlib.Sys_error
  exception End_of_file = Stdlib.End_of_file
  exception Division_by_zero = Stdlib.Division_by_zero
  exception Sys_blocked_io = Stdlib.Sys_blocked_io
  exception Undefined_recursive_module = Stdlib.Undefined_recursive_module
  external ( = ) : 'a -> 'a -> bool = "%equal"
  external ( <> ) : 'a -> 'a -> bool = "%notequal"
  external ( < ) : 'a -> 'a -> bool = "%lessthan"
  external ( > ) : 'a -> 'a -> bool = "%greaterthan"
  external ( <= ) : 'a -> 'a -> bool = "%lessequal"
  external ( >= ) : 'a -> 'a -> bool = "%greaterequal"
  external compare : 'a -> 'a -> int = "%compare"
  let min = Stdlib.min
  let max = Stdlib.max
  external ( == ) : 'a -> 'a -> bool = "%eq"
  external ( != ) : 'a -> 'a -> bool = "%noteq"
  external not : bool -> bool = "%boolnot"
  external ( && ) : bool -> bool -> bool = "%sequand"
  external ( & ) : bool -> bool -> bool = "%sequand"
  external ( || ) : bool -> bool -> bool = "%sequor"
  external ( or ) : bool -> bool -> bool = "%sequor"
  external __LOC__ : string = "%loc_LOC"
  external __FILE__ : string = "%loc_FILE"
  external __LINE__ : int = "%loc_LINE"
  external __MODULE__ : string = "%loc_MODULE"
  external __POS__ : string * int * int = "%loc_POS"
  external __FUNCTION__ : string = "%loc_FUNCTION"
  external __LOC_OF__ : 'a -> string * 'a = "%loc_LOC"
  external __LINE_OF__ : 'a -> int * 'a = "%loc_LINE"
  external __POS_OF__ : 'a -> (string * int * int) * 'a = "%loc_POS"
  external ( |> ) : 'a -> ('a -> 'b) -> 'b = "%revapply"
  external ( @@ ) : ('a -> 'b) -> 'a -> 'b = "%apply"
  external ( ~- ) : int -> int = "%negint"
  external ( ~+ ) : int -> int = "%identity"
  external succ : int -> int = "%succint"
  external pred : int -> int = "%predint"
  external ( + ) : int -> int -> int = "%addint"
  external ( - ) : int -> int -> int = "%subint"
  external ( * ) : int -> int -> int = "%mulint"
  external ( / ) : int -> int -> int = "%divint"
  external ( mod ) : int -> int -> int = "%modint"
  let abs = Stdlib.abs
  let max_int = Stdlib.max_int
  let min_int = Stdlib.min_int
  external ( land ) : int -> int -> int = "%andint"
  external ( lor ) : int -> int -> int = "%orint"
  external ( lxor ) : int -> int -> int = "%xorint"
  let lnot = Stdlib.lnot
  external ( lsl ) : int -> int -> int = "%lslint"
  external ( lsr ) : int -> int -> int = "%lsrint"
  external ( asr ) : int -> int -> int = "%asrint"
  external ( ~-. ) : float -> float = "%negfloat"
  external ( ~+. ) : float -> float = "%identity"
  external ( +. ) : float -> float -> float = "%addfloat"
  external ( -. ) : float -> float -> float = "%subfloat"
  external ( *. ) : float -> float -> float = "%mulfloat"
  external ( /. ) : float -> float -> float = "%divfloat"
  external ( ** ) : float -> float -> float = "caml_power_float" "pow" [@@unboxed] [@@noalloc]
  external sqrt : float -> float = "caml_sqrt_float" "sqrt" [@@unboxed] [@@noalloc]
  external exp : float -> float = "caml_exp_float" "exp" [@@unboxed] [@@noalloc]
  external log : float -> float = "caml_log_float" "log" [@@unboxed] [@@noalloc]
  external log10 : float -> float = "caml_log10_float" "log10" [@@unboxed] [@@noalloc]
  external expm1 : float -> float = "caml_expm1_float" "caml_expm1" [@@unboxed] [@@noalloc]
  external log1p : float -> float = "caml_log1p_float" "caml_log1p" [@@unboxed] [@@noalloc]
  external cos : float -> float = "caml_cos_float" "cos" [@@unboxed] [@@noalloc]
  external sin : float -> float = "caml_sin_float" "sin" [@@unboxed] [@@noalloc]
  external tan : float -> float = "caml_tan_float" "tan" [@@unboxed] [@@noalloc]
  external acos : float -> float = "caml_acos_float" "acos" [@@unboxed] [@@noalloc]
  external asin : float -> float = "caml_asin_float" "asin" [@@unboxed] [@@noalloc]
  external atan : float -> float = "caml_atan_float" "atan" [@@unboxed] [@@noalloc]
  external atan2 : float -> float -> float = "caml_atan2_float" "atan2" [@@unboxed] [@@noalloc]
  external hypot : float -> float -> float = "caml_hypot_float" "caml_hypot" [@@unboxed] [@@noalloc]
  external cosh : float -> float = "caml_cosh_float" "cosh" [@@unboxed] [@@noalloc]
  external sinh : float -> float = "caml_sinh_float" "sinh" [@@unboxed] [@@noalloc]
  external tanh : float -> float = "caml_tanh_float" "tanh" [@@unboxed] [@@noalloc]
  external acosh : float -> float = "caml_acosh_float" "caml_acosh" [@@unboxed] [@@noalloc]
  external asinh : float -> float = "caml_asinh_float" "caml_asinh" [@@unboxed] [@@noalloc]
  external atanh : float -> float = "caml_atanh_float" "caml_atanh" [@@unboxed] [@@noalloc]
  external ceil : float -> float = "caml_ceil_float" "ceil" [@@unboxed] [@@noalloc]
  external floor : float -> float = "caml_floor_float" "floor" [@@unboxed] [@@noalloc]
  external abs_float : float -> float = "%absfloat"
  external copysign : float -> float -> float = "caml_copysign_float" "caml_copysign" [@@unboxed] [@@noalloc]
  external mod_float : float -> float -> float = "caml_fmod_float" "fmod" [@@unboxed] [@@noalloc]
  external frexp : float -> float * int = "caml_frexp_float"
  external ldexp : (float [@unboxed]) -> (int [@untagged]) -> (float [@unboxed])  = "caml_ldexp_float" "caml_ldexp_float_unboxed" [@@noalloc]
  external modf : float -> float * float = "caml_modf_float"
  external float : int -> float = "%floatofint"
  external float_of_int : int -> float = "%floatofint"
  external truncate : float -> int = "%intoffloat"
  external int_of_float : float -> int = "%intoffloat"
  let infinity = Stdlib.infinity
  let neg_infinity = Stdlib.neg_infinity
  let nan = Stdlib.nan
  let max_float = Stdlib.max_float
  let min_float = Stdlib.min_float
  let epsilon_float = Stdlib.epsilon_float
  type fpclass = Stdlib.fpclass
  external classify_float : (float [@unboxed]) -> fpclass = "caml_classify_float" "caml_classify_float_unboxed" [@@noalloc]
  let ( ^ ) = Stdlib.( ^ )
  external int_of_char : char -> int = "%identity"
  let char_of_int = Stdlib.char_of_int
  external ignore : 'a -> unit = "%ignore"
  let string_of_bool = Stdlib.string_of_bool
  let bool_of_string_opt = Stdlib.bool_of_string_opt
  let bool_of_string = Stdlib.bool_of_string
  let string_of_int = Stdlib.string_of_int
  let int_of_string_opt = Stdlib.int_of_string_opt
  external int_of_string : string -> int = "caml_int_of_string"
  let string_of_float = Stdlib.string_of_float
  let float_of_string_opt = Stdlib.float_of_string_opt
  external float_of_string : string -> float = "caml_float_of_string"
  external fst : 'a * 'b -> 'a = "%field0"
  external snd : 'a * 'b -> 'b = "%field1"
  let ( @ ) = Stdlib.( @ )
  (* type in_channel *)
  (* type out_channel *)
  (* val stdin : in_channel *)
  (* val stdout : out_channel *)
  (* val stderr : out_channel *)
  (* val print_char : char -> unit *)
  (* val print_string : string -> unit *)
  (* val print_bytes : bytes -> unit *)
  (* val print_int : int -> unit *)
  (* val print_float : float -> unit *)
  (* val print_endline : string -> unit *)
  (* val print_newline : unit -> unit *)
  (* val prerr_char : char -> unit *)
  (* val prerr_string : string -> unit *)
  (* val prerr_bytes : bytes -> unit *)
  (* val prerr_int : int -> unit *)
  (* val prerr_float : float -> unit *)
  (* val prerr_endline : string -> unit *)
  (* val prerr_newline : unit -> unit *)
  (* val read_line : unit -> string *)
  (* val read_int_opt : unit -> int option *)
  (* val read_int : unit -> int *)
  (* val read_float_opt : unit -> float option *)
  (* val read_float : unit -> float *)
  (* type open_flag = *)
  (* Open_rdonly *)
  (* | Open_wronly *)
  (* | Open_append *)
  (* | Open_creat *)
  (* | Open_trunc *)
  (* | Open_excl *)
  (* | Open_binary *)
  (* | Open_text *)
  (* | Open_nonblock *)
  (* val open_out : string -> out_channel *)
  (* val open_out_bin : string -> out_channel *)
  (* val open_out_gen : open_flag list -> int -> string -> out_channel *)
  (* val flush : out_channel -> unit *)
  (* val flush_all : unit -> unit *)
  (* val output_char : out_channel -> char -> unit *)
  (* val output_string : out_channel -> string -> unit *)
  (* val output_bytes : out_channel -> bytes -> unit *)
  (* val output : out_channel -> bytes -> int -> int -> unit *)
  (* val output_substring : out_channel -> string -> int -> int -> unit *)
  (* val output_byte : out_channel -> int -> unit *)
  (* val output_binary_int : out_channel -> int -> unit *)
  (* val output_value : out_channel -> 'a -> unit *)
  (* val seek_out : out_channel -> int -> unit *)
  (* val pos_out : out_channel -> int *)
  (* val out_channel_length : out_channel -> int *)
  (* val close_out : out_channel -> unit *)
  (* val close_out_noerr : out_channel -> unit *)
  (* val set_binary_mode_out : out_channel -> bool -> unit *)
  (* val open_in : string -> in_channel *)
  (* val open_in_bin : string -> in_channel *)
  (* val open_in_gen : open_flag list -> int -> string -> in_channel *)
  (* val input_char : in_channel -> char *)
  (* val input_line : in_channel -> string *)
  (* val input : in_channel -> bytes -> int -> int -> int *)
  (* val really_input : in_channel -> bytes -> int -> int -> unit *)
  (* val really_input_string : in_channel -> int -> string *)
  (* val input_byte : in_channel -> int *)
  (* val input_binary_int : in_channel -> int *)
  (* val input_value : in_channel -> 'a *)
  (* val seek_in : in_channel -> int -> unit *)
  (* val pos_in : in_channel -> int *)
  (* val in_channel_length : in_channel -> int *)
  (* val close_in : in_channel -> unit *)
  (* val close_in_noerr : in_channel -> unit *)
  (* val set_binary_mode_in : in_channel -> bool -> unit *)
  (* module LargeFile : sig ... end *)
  (* type 'a ref = { mutable contents : 'a; } *)
  (* external ref : 'a -> 'a ref = "%makemutable" *)
  (* external ( ! ) : 'a ref -> 'a = "%field0" *)
  (* external ( := ) : 'a ref -> 'a -> unit = "%setfield0" *)
  (* external incr : int ref -> unit = "%incr" *)
  (* external decr : int ref -> unit = "%decr" *)
  type ('a, 'b) result = ('a, 'b) Stdlib.result
  type ('a, 'b, 'c, 'd, 'e, 'f) format6 = ('a, 'b, 'c, 'd, 'e, 'f) Stdlib.format6
  type ('a, 'b, 'c, 'd) format4 = ('a, 'b, 'c, 'd) Stdlib.format4
  type ('a, 'b, 'c) format = ('a, 'b, 'c) Stdlib.format
  let string_of_format = Stdlib.string_of_format
  external format_of_string : ('a, 'b, 'c, 'd, 'e, 'f) format6 -> ('a, 'b, 'c, 'd, 'e, 'f) format6 = "%identity"
  let ( ^^ ) = Stdlib.( ^^ )
  (* val exit : int -> 'a *)
  (* val at_exit : (unit -> unit) -> unit *)
  (* val valid_float_lexem : string -> string *)
  (* val unsafe_really_input : in_channel -> bytes -> int -> int -> unit *)
  (* val do_at_exit : unit -> unit *)

  (***********)
  (* Modules *)
  (***********)
  (* module Arg = Stdlib.Arg *)
  (* module Array = Stdlib.Array *)
  (* module ArrayLabels = Stdlib.ArrayLabels *)
  (* module Atomic = Stdlib.Atomic *)
  (* module Bigarray = Stdlib.Bigarray *)
  module Bool = Stdlib.Bool
  module Buffer = Stdlib.Buffer
  module Bytes = Stdlib.Bytes
  module BytesLabels = Stdlib.BytesLabels
  (* module Callback = Stdlib.Callback *)
  module Char = Stdlib.Char
  module Complex = Stdlib.Complex
  module Digest = Stdlib.Digest
  module Either = Stdlib.Either
  (* module Ephemeron = Stdlib.Ephemeron *)
  module Filename = Stdlib.Filename
  module Float = Stdlib.Float
  module Format = Stdlib.Format
  module Fun = Stdlib.Fun
  (* module Gc = Stdlib.Gc *)
  (* module Genlex = Stdlib.Genlex *)
  module Hashtbl = Stdlib.Hashtbl
  module Int = Stdlib.Int
  module Int32 = Stdlib.Int32
  module Int64 = Stdlib.Int64
  module Lazy = Stdlib.Lazy
  module Lexing = Stdlib.Lexing
  module List = Stdlib.List
  module ListLabels = Stdlib.ListLabels
  module Map = Stdlib.Map
  (* module Marshal = Stdlib.Marshal *)
  module MoreLabels = Stdlib.MoreLabels
  module Nativeint = Stdlib.Nativeint
  (* module Obj = Stdlib.Obj *)
  (* module Oo = Stdlib.Oo *)
  module Option = Stdlib.Option
  module Parsing = Stdlib.Parsing
  (* module Pervasives = Stdlib.Pervasives *) (* DEPECATED CAUSES ERROR ON USE *)
  (* module Printexc = Stdlib.Printexc *)
  module Printf = Stdlib.Printf
  module Queue = Stdlib.Queue
  module Random = Stdlib.Random
  module Result = Stdlib.Result
  module Scanf = Stdlib.Scanf
  module Seq = Stdlib.Seq
  module Set = Stdlib.Set
  module Stack = Stdlib.Stack
  module StdLabels = Stdlib.StdLabels
  (* module Stream = Stdlib.Stream *)
  module String = Stdlib.String
  module StringLabels = Stdlib.StringLabels
  (* module Sys = Stdlib.Sys *)
  module Uchar = Stdlib.Uchar
  module Unit = Stdlib.Unit
  module Weak = Stdlib.Weak
end


(****************************************************)
(* Make overrides available with the original names *)
(****************************************************)

module Stdlib = OVERRIDE__STDLIB
(* and open stdlib *)
include OVERRIDE__STDLIB

(*
  hide all of the stdlibs internal implementation modules,
  even the ones that belong to allowed modules,
  as they should never be used directly anyways
*)
module Stdlib__MoreLabels = struct end
module Stdlib__Array = struct end
module Stdlib__Atomic = struct end
module Stdlib__Stack = struct end
module Stdlib__Uchar = struct end
module Stdlib__Seq = struct end
module Stdlib__Int = struct end
module Stdlib__Bytes = struct end
module Stdlib__Weak = struct end
module Stdlib__Printf = struct end
module Stdlib__Filename = struct end
module Stdlib__Stream = struct end
module Stdlib__Arg = struct end
module Stdlib__Gc = struct end
module Stdlib__Scanf = struct end
module Stdlib__Digest = struct end
module Stdlib__Hashtbl = struct end
module Stdlib__Set = struct end
module Stdlib__Queue = struct end
module Stdlib__Either = struct end
module Stdlib__Printexc = struct end
module Stdlib__Bool = struct end
module Stdlib__Lexing = struct end
module Stdlib__Buffer = struct end
module Stdlib__ArrayLabels = struct end
module Stdlib__Pervasives = struct end
module Stdlib__Oo = struct end
module Stdlib__StringLabels = struct end
module Stdlib__Ephemeron = struct end
module Stdlib__Callback = struct end
module Stdlib__Bigarray = struct end
module Stdlib__Genlex = struct end
module Stdlib__Result = struct end
module Stdlib__Lazy = struct end
module Stdlib__List = struct end
module Stdlib__Int32 = struct end
module Stdlib__Map = struct end
module Stdlib__Complex = struct end
module Stdlib__Float = struct end
module Stdlib__ListLabels = struct end
module Stdlib__BytesLabels = struct end
module Stdlib__Format = struct end
module Stdlib__Marshal = struct end
module Stdlib__Option = struct end
module Stdlib__Random = struct end
module Stdlib__Sys = struct end
module Stdlib__Int64 = struct end
module Stdlib__Parsing = struct end
module Stdlib__Char = struct end
module Stdlib__Fun = struct end
module Stdlib__Obj = struct end
module Stdlib__String = struct end
module Stdlib__Nativeint = struct end
module Stdlib__Unit = struct end
module Stdlib__StdLabels = struct end

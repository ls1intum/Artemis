let add a b = a + b

let rec filter p = function
  | [] -> []
  | a :: t -> if p a then a :: filter p t else filter p t

let starts_with haystack needle = String.starts_with ~prefix:needle haystack

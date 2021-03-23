let add a b = a + b

let rec filter p = function
  | [] -> []
  | a :: t -> if p a then a :: (filter p t) else filter p t

let starts_with = BatString.starts_with

let starts_with2 haysteck hay = Base.String.is_prefix haysteck ~prefix:hay

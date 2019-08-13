import inspect


def check_class_names(package, *args):
    cls = inspect.getmembers(package, lambda a: inspect.isClass(a))
    _check_names(cls, args)


def check_constructor_args(clazz, *args):
    arguments = inspect.getargspec(clazz.__init__).args
    for arg in args:
        assert arg in arguments


def check_method_names(package, *args):
    cls = inspect.getmembers(package, lambda a: inspect.isMethod(a))
    _check_names(cls, args)


def check_abstract_method_names(clazz, *args):
    cls = inspect.getmembers(clazz, lambda a: not(inspect.isroutine(a)))
    cls = [c for c in cls if c[0] is '__abstractmethods__']
    cls = list(cls[0][1])

    for name in args:
        assert name in cls


def check_attributes(clazz, *args):
    cls = inspect.getmembers(clazz, lambda a: not(inspect.isroutine(a)))
    attributes = [attr for attr in cls if not(attr[0].startswith('__') and attr[0].endswith('__'))]

    _check_names(attributes, args)


def _check_names(cls, *args):
    names = list(map(lambda c: c[0], cls))

    for name in args:
        assert name in names

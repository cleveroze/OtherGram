"""OtherGram plugin API: reflection helpers (compatible with exteraGram's `hook_utils`)."""

from java import jclass

_Bridge = jclass("org.telegram.othergram.plugins.OtherGramPluginBridge")


def find_class(name):
    try:
        return jclass(name)
    except Exception:
        return None


def get_private_field(obj, name):
    return _Bridge.getField(obj, str(name))


def set_private_field(obj, name, value):
    _Bridge.setField(obj, str(name), value)


def get_static_private_field(cls, name):
    return _Bridge.getStaticField(cls, str(name))

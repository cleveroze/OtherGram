"""OtherGram plugin API: Android helpers (compatible with exteraGram's `android_utils`)."""

from java import dynamic_proxy, jclass

_Bridge = jclass("org.telegram.othergram.plugins.OtherGramPluginBridge")
_Runnable = jclass("java.lang.Runnable")
_OnClickListener = jclass("android.view.View$OnClickListener")


class R(dynamic_proxy(_Runnable)):
    """A Java Runnable that calls a Python function."""

    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def run(self):
        try:
            self.fn()
        except Exception as e:
            _Bridge.log("plugin", "Runnable failed: %r" % (e,))


class OnClickListener(dynamic_proxy(_OnClickListener)):
    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def onClick(self, view):
        try:
            self.fn(view)
        except TypeError:
            self.fn()


def run_on_ui_thread(fn, delay=0):
    _Bridge.runOnUiThread(R(fn), int(delay))


def log(message):
    _Bridge.log("plugin", str(message))


def copy_to_clipboard(text):
    _Bridge.copyToClipboard(str(text))

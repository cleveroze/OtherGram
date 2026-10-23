"""OtherGram plugin API: simple dialogs (subset of exteraGram's `ui.alert`)."""

from java import jclass

from android_utils import run_on_ui_thread

_Bridge = jclass("org.telegram.othergram.plugins.OtherGramPluginBridge")


class AlertDialogBuilder:
    ALERT_TYPE_MESSAGE = 0

    def __init__(self, context=None, progress_style=0):
        self._title = None
        self._message = None
        self._positive = None
        self._negative = None

    def set_title(self, title):
        self._title = str(title)
        return self

    def set_message(self, message):
        self._message = str(message)
        return self

    def set_positive_button(self, text, listener=None):
        self._positive = (str(text), listener)
        return self

    def set_negative_button(self, text, listener=None):
        self._negative = (str(text), listener)
        return self

    def show(self):
        from android_utils import R
        positive = self._positive or ("OK", None)
        negative = self._negative
        on_positive = R(lambda: positive[1](self, 0)) if positive[1] else None
        on_negative = R(lambda: negative[1](self, 1)) if negative and negative[1] else None
        _Bridge.showAlert(self._title, self._message, positive[0], on_positive,
                          negative[0] if negative else None, on_negative)
        return self

    def dismiss(self):
        pass

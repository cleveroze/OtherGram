"""OtherGram plugin API: short pop-up messages at the bottom (compatible with exteraGram's `ui.bulletin`)."""

from java import jclass

_Bridge = jclass("org.telegram.othergram.plugins.OtherGramPluginBridge")


class BulletinHelper:
    @staticmethod
    def show_info(message, fragment=None):
        _Bridge.showBulletin("info", str(message))

    @staticmethod
    def show_error(message, fragment=None):
        _Bridge.showBulletin("error", str(message))

    @staticmethod
    def show_success(message, fragment=None):
        _Bridge.showBulletin("success", str(message))

    @staticmethod
    def show_simple(message, icon_res_id=None, fragment=None):
        _Bridge.showBulletin("info", str(message))

    @staticmethod
    def show_copied_to_clipboard(message=None, fragment=None):
        _Bridge.showBulletin("success", str(message) if message else "Copied")

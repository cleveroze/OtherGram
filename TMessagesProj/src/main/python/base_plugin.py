"""
OtherGram plugin API, compatible with exteraGram's `base_plugin` module.

A plugin is a .plugin file (Python) with metadata (__id__, __name__, __version__, ...) and a class
derived from BasePlugin. Supported: lifecycle, settings, send-message hooks, request hooks
(add_hook + pre_request_hook / post_request_hook), message menu items, app events. Not supported:
Xposed-style method hooks (hook_method etc.) and update hooks — those calls do nothing, but the
plugin still loads and everything else in it works.
"""

import json
from enum import Enum

from java import jclass

_Bridge = jclass("org.telegram.othergram.plugins.OtherGramPluginBridge")


class HookStrategy(Enum):
    DEFAULT = 0
    CANCEL = 1
    MODIFY = 2
    MODIFY_FINAL = 3


class HookResult:
    def __init__(self, strategy=HookStrategy.DEFAULT, request=None, response=None, update=None, updates=None, params=None):
        self.strategy = strategy
        self.request = request
        self.response = response
        self.update = update
        self.updates = updates
        self.params = params


class AppEvent(Enum):
    START = "START"
    STOP = "STOP"
    PAUSE = "PAUSE"
    RESUME = "RESUME"


class MenuItemType(Enum):
    MESSAGE_CONTEXT_MENU = "MESSAGE_CONTEXT_MENU"
    DRAWER_MENU = "DRAWER_MENU"
    CHAT_ACTION_MENU = "CHAT_ACTION_MENU"
    PROFILE_ACTION_MENU = "PROFILE_ACTION_MENU"
    MAIN_MENU = "MAIN_MENU"


class MenuItemData:
    def __init__(self, menu_type, text, on_click=None, icon=None, subtext=None, item_id=None, priority=0, condition=None):
        self.menu_type = menu_type
        self.text = text
        self.on_click = on_click
        self.icon = icon
        self.subtext = subtext
        self.item_id = item_id
        self.priority = priority
        self.condition = condition


def _hooks_changed():
    """Replaced by the plugin host: tells the app which request names plugins listen to."""
    pass


class _NoHook:
    def unhook(self):
        pass


class MethodHook:
    """Base class plugins use with hook_method (kept so such plugins load; the hook itself is not called)."""

    def before_hooked_method(self, param):
        pass

    def after_hooked_method(self, param):
        pass


class MethodReplacement(MethodHook):
    def replace_hooked_method(self, param):
        return None


class XposedHook(MethodHook):
    pass


class BasePlugin:
    def __init__(self):
        self.id = None
        self.name = None
        self.error_message = None
        self._send_hook_priority = None
        self._request_hooks = []
        self._menu_items = {}
        self._menu_counter = 0
        self._unsupported = []

    # ---------------------------------------------------------------- lifecycle

    def on_plugin_load(self):
        pass

    def on_plugin_unload(self):
        pass

    def on_app_event(self, event_type):
        pass

    # ---------------------------------------------------------------- settings

    def create_settings(self):
        return []

    def get_setting(self, key, default=None):
        raw = _Bridge.getSetting(self.id, str(key))
        if raw is None:
            return default
        try:
            return json.loads(raw)
        except Exception:
            return default

    def set_setting(self, key, value, reload_settings=False):
        _Bridge.setSetting(self.id, str(key), json.dumps(value))
        if reload_settings:
            _Bridge.reloadSettings(self.id)

    def export_settings(self):
        raw = _Bridge.exportSettings(self.id)
        return json.loads(raw) if raw else {}

    def import_settings(self, settings, reload_settings=True):
        for key, value in (settings or {}).items():
            _Bridge.setSetting(self.id, str(key), json.dumps(value))
        if reload_settings:
            _Bridge.reloadSettings(self.id)

    # ---------------------------------------------------------------- hooks

    def add_on_send_message_hook(self, priority=0):
        self._send_hook_priority = priority

    def remove_on_send_message_hook(self):
        self._send_hook_priority = None

    def on_send_message_hook(self, account, params):
        return HookResult()

    def add_hook(self, name, match_substring=False, priority=0):
        """Calls pre_request_hook / post_request_hook for requests named like the TL class, e.g. "TL_messages_readHistory"."""
        self._request_hooks.append((str(name), bool(match_substring), priority))
        _hooks_changed()

    def remove_hook(self, name):
        self._request_hooks = [h for h in self._request_hooks if h[0] != name]
        _hooks_changed()

    def pre_request_hook(self, request_name, account, request):
        return HookResult()

    def post_request_hook(self, request_name, account, response, error):
        return HookResult()

    def on_update_hook(self, update_name, account, update):
        return HookResult()

    def on_updates_hook(self, container_name, account, updates):
        return HookResult()

    # Method hooks (Xposed style) are not available in OtherGram. They do not crash the plugin:
    # the call returns a handle that does nothing, so the rest of the plugin keeps working.

    def hook_method(self, *args, **kwargs):
        self._unsupported.append("hook_method")
        return _NoHook()

    def hook_all_methods(self, *args, **kwargs):
        self._unsupported.append("hook_all_methods")
        return [_NoHook()]

    def hook_all_constructors(self, *args, **kwargs):
        self._unsupported.append("hook_all_constructors")
        return [_NoHook()]

    def unhook_method(self, *args, **kwargs):
        pass

    # ---------------------------------------------------------------- menu items

    def add_menu_item(self, menu_item_data):
        self._menu_counter += 1
        item_id = menu_item_data.item_id or "%s_%d" % (self.id, self._menu_counter)
        menu_item_data.item_id = item_id
        self._menu_items[item_id] = menu_item_data
        return item_id

    def remove_menu_item(self, item_id):
        self._menu_items.pop(item_id, None)

    # ---------------------------------------------------------------- misc

    def log(self, message):
        _Bridge.log(str(self.id), str(message))

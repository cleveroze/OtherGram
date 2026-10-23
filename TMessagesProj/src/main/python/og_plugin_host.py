"""
OtherGram plugin host: loads .plugin files and dispatches calls from the app (Java) to them.
Every call is wrapped so a broken plugin reports an error instead of crashing OtherGram.
"""

import json
import re
import traceback
import types

from base_plugin import AppEvent, BasePlugin, HookStrategy, MenuItemType

_plugins = {}


def _error():
    return traceback.format_exc(limit=6)


def load(plugin_id, path):
    """Returns "" on success or the error text."""
    unload(plugin_id)
    try:
        with open(path, encoding="utf-8") as f:
            source = f.read()
        module = types.ModuleType("og_plugin_" + re.sub(r"[^A-Za-z0-9_]", "_", plugin_id))
        module.__file__ = path
        exec(compile(source, path, "exec"), module.__dict__)
        plugin_class = None
        for value in list(module.__dict__.values()):
            if isinstance(value, type) and issubclass(value, BasePlugin) and value is not BasePlugin:
                plugin_class = value
        if plugin_class is None:
            return "No class derived from BasePlugin was found in the plugin."
        instance = plugin_class()
        instance.id = plugin_id
        instance.name = module.__dict__.get("__name__", plugin_id)
        _plugins[plugin_id] = instance
        instance.on_plugin_load()
        return ""
    except Exception:
        _plugins.pop(plugin_id, None)
        return _error()


def unload(plugin_id):
    instance = _plugins.pop(plugin_id, None)
    if instance is not None:
        try:
            instance.on_plugin_unload()
        except Exception:
            pass


def loaded_ids():
    return ",".join(_plugins.keys())


def app_event(name):
    try:
        event = AppEvent(name)
    except ValueError:
        return
    for instance in list(_plugins.values()):
        try:
            instance.on_app_event(event)
        except Exception:
            pass


# ---------------------------------------------------------------- send message hook

def has_send_hooks():
    return any(p._send_hook_priority is not None for p in _plugins.values())


def on_send_message(account, params):
    """Returns True when a plugin cancelled sending. Plugins edit `params` (Java object) in place."""
    hooked = [p for p in _plugins.values() if p._send_hook_priority is not None]
    hooked.sort(key=lambda p: -p._send_hook_priority)
    for instance in hooked:
        try:
            result = instance.on_send_message_hook(account, params)
        except Exception:
            instance.error_message = _error()
            continue
        if result is None:
            continue
        strategy = getattr(result, "strategy", HookStrategy.DEFAULT)
        if strategy == HookStrategy.CANCEL:
            return True
        if strategy in (HookStrategy.MODIFY, HookStrategy.MODIFY_FINAL):
            new_params = getattr(result, "params", None)
            if new_params is not None and new_params is not params:
                try:
                    params.message = new_params.message
                except Exception:
                    pass
            if strategy == HookStrategy.MODIFY_FINAL:
                break
    return False


def last_error(plugin_id):
    instance = _plugins.get(plugin_id)
    return (instance.error_message or "") if instance else ""


# ---------------------------------------------------------------- settings

def _settings_items(plugin_id):
    instance = _plugins.get(plugin_id)
    if instance is None:
        return None, []
    try:
        return instance, list(instance.create_settings() or [])
    except Exception:
        instance.error_message = _error()
        return instance, []


def settings_json(plugin_id):
    instance, items = _settings_items(plugin_id)
    out = []
    for index, item in enumerate(items):
        kind = getattr(item, "type", "custom")
        entry = {"index": index, "type": kind, "text": str(getattr(item, "text", "") or "")}
        subtext = getattr(item, "subtext", None)
        if subtext:
            entry["subtext"] = str(subtext)
        if kind in ("switch", "input", "selector"):
            value = instance.get_setting(item.key, item.default)
            if kind == "switch":
                entry["value"] = bool(value)
            elif kind == "input":
                entry["value"] = "" if value is None else str(value)
            else:
                entry["items"] = [str(x) for x in item.items]
                try:
                    entry["value"] = int(value)
                except Exception:
                    entry["value"] = 0
        if kind == "text":
            entry["accent"] = bool(getattr(item, "accent", False))
            entry["red"] = bool(getattr(item, "red", False))
        out.append(entry)
    return json.dumps(out)


def settings_action(plugin_id, index, value_json):
    """A settings row changed or was tapped. Returns "" or the error text."""
    instance, items = _settings_items(plugin_id)
    if instance is None or index < 0 or index >= len(items):
        return ""
    item = items[index]
    kind = getattr(item, "type", "")
    try:
        value = json.loads(value_json) if value_json else None
        if kind in ("switch", "input", "selector"):
            instance.set_setting(item.key, value)
            if getattr(item, "on_change", None):
                item.on_change(value)
        elif kind == "text" and getattr(item, "on_click", None):
            try:
                item.on_click(None)
            except TypeError:
                item.on_click()
        return ""
    except Exception:
        instance.error_message = _error()
        return instance.error_message


# ---------------------------------------------------------------- menu items

def menu_items_json(menu_type):
    out = []
    for instance in _plugins.values():
        for item_id, data in list(instance._menu_items.items()):
            mt = data.menu_type.value if isinstance(data.menu_type, MenuItemType) else str(data.menu_type)
            if mt != menu_type:
                continue
            out.append({"plugin": instance.id, "item": item_id, "text": str(data.text), "priority": data.priority or 0})
    out.sort(key=lambda e: -e["priority"])
    return json.dumps(out)


def menu_click(plugin_id, item_id, account, fragment, dialog_id, message):
    instance = _plugins.get(plugin_id)
    if instance is None:
        return ""
    data = instance._menu_items.get(item_id)
    if data is None or data.on_click is None:
        return ""
    context = {"account": account, "fragment": fragment, "dialog_id": dialog_id, "message": message}
    if fragment is not None:
        try:
            context["context"] = fragment.getParentActivity()
        except Exception:
            pass
    try:
        data.on_click(context)
        return ""
    except Exception:
        instance.error_message = _error()
        return instance.error_message

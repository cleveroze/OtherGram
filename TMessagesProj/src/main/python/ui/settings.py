"""OtherGram plugin API: items a plugin returns from create_settings() (compatible with exteraGram's `ui.settings`)."""


class Header:
    def __init__(self, text):
        self.type = "header"
        self.text = text


class Divider:
    def __init__(self, text=None):
        self.type = "divider"
        self.text = text


class Switch:
    def __init__(self, key, text, default=False, subtext=None, icon=None, on_change=None, on_long_click=None, link_alias=None):
        self.type = "switch"
        self.key = key
        self.text = text
        self.default = default
        self.subtext = subtext
        self.icon = icon
        self.on_change = on_change


class Input:
    def __init__(self, key, text, default="", subtext=None, icon=None, on_change=None, link_alias=None):
        self.type = "input"
        self.key = key
        self.text = text
        self.default = default
        self.subtext = subtext
        self.icon = icon
        self.on_change = on_change


class EditText(Input):
    def __init__(self, key, hint="", default="", multiline=False, max_length=0, mask=None, on_change=None):
        super().__init__(key, hint, default, on_change=on_change)


class Selector:
    def __init__(self, key, text, default=0, items=None, icon=None, on_change=None, link_alias=None):
        self.type = "selector"
        self.key = key
        self.text = text
        self.default = default
        self.items = list(items or [])
        self.icon = icon
        self.on_change = on_change


class Text:
    def __init__(self, text, icon=None, accent=False, red=False, on_click=None, create_sub_fragment=None, link_alias=None, subtext=None):
        self.type = "text"
        self.text = text
        self.icon = icon
        self.accent = accent
        self.red = red
        self.on_click = on_click
        self.subtext = subtext


class Custom:
    def __init__(self, *args, **kwargs):
        self.type = "custom"
        self.text = None

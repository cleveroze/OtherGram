"""
OtherGram plugin API: minimal `markdown_utils`. Turns **bold**, __italic__, `code` and ~~strike~~ into
plain text plus Telegram message entities, enough for plugins that send formatted text.
"""

import re

from java import jclass

_TLRPC = "org.telegram.tgnet.TLRPC$"
_PATTERNS = [
    (re.compile(r"\*\*(.+?)\*\*", re.S), "TL_messageEntityBold"),
    (re.compile(r"__(.+?)__", re.S), "TL_messageEntityItalic"),
    (re.compile(r"~~(.+?)~~", re.S), "TL_messageEntityStrike"),
    (re.compile(r"`(.+?)`", re.S), "TL_messageEntityCode"),
]


class ParsedMessage:
    def __init__(self, text, entities):
        self.text = text
        self.entities = entities


def _utf16_len(s):
    return len(s.encode("utf-16-le")) // 2


def parse_markdown(text):
    entities = []
    for pattern, entity_class in _PATTERNS:
        out = []
        last = 0
        for m in pattern.finditer(text):
            out.append(text[last:m.start()])
            offset = _utf16_len("".join(out))
            inner = m.group(1)
            out.append(inner)
            try:
                entity = jclass(_TLRPC + entity_class)()
                entity.offset = offset
                entity.length = _utf16_len(inner)
                entities.append(entity)
            except Exception:
                pass
            last = m.end()
        out.append(text[last:])
        text = "".join(out)
    return ParsedMessage(text, entities)

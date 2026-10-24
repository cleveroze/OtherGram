"""OtherGram plugin API: file helpers (compatible with exteraGram's `file_utils`)."""

import os

from java import jclass

_ApplicationLoader = jclass("org.telegram.messenger.ApplicationLoader")


def _context():
    return _ApplicationLoader.applicationContext


def get_files_dir():
    return str(_context().getFilesDir().getAbsolutePath())


def get_cache_dir():
    return str(_context().getCacheDir().getAbsolutePath())


def get_plugins_dir():
    return os.path.join(get_files_dir(), "plugins")


def ensure_dir_exists(path):
    os.makedirs(path, exist_ok=True)
    return path


def read_file(path, mode="r", encoding="utf-8"):
    with open(path, mode, encoding=None if "b" in mode else encoding) as f:
        return f.read()


def write_file(path, content, mode=None, encoding="utf-8"):
    if mode is None:
        mode = "wb" if isinstance(content, (bytes, bytearray)) else "w"
    directory = os.path.dirname(path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    with open(path, mode, encoding=None if "b" in mode else encoding) as f:
        f.write(content)
    return path


def delete_file(path):
    try:
        os.remove(path)
        return True
    except OSError:
        return False


def list_dir(path):
    try:
        return os.listdir(path)
    except OSError:
        return []

"""OtherGram plugin API: Telegram client helpers (compatible with exteraGram's `client_utils`)."""

from java import dynamic_proxy, jclass

from android_utils import R

_Bridge = jclass("org.telegram.othergram.plugins.OtherGramPluginBridge")
_UserConfig = jclass("org.telegram.messenger.UserConfig")
_AccountInstance = jclass("org.telegram.messenger.AccountInstance")
_LaunchActivity = jclass("org.telegram.ui.LaunchActivity")
_RequestDelegate = jclass("org.telegram.tgnet.RequestDelegate")


def get_selected_account():
    return _UserConfig.selectedAccount


def get_account_instance(account=None):
    return _AccountInstance.getInstance(get_selected_account() if account is None else account)


def get_messages_controller(account=None):
    return get_account_instance(account).getMessagesController()


def get_connections_manager(account=None):
    return get_account_instance(account).getConnectionsManager()


def get_user_config(account=None):
    return get_account_instance(account).getUserConfig()


def get_send_messages_helper(account=None):
    return get_account_instance(account).getSendMessagesHelper()


def get_media_data_controller(account=None):
    return get_account_instance(account).getMediaDataController()


def get_notification_center(account=None):
    return get_account_instance(account).getNotificationCenter()


def get_messages_storage(account=None):
    return get_account_instance(account).getMessagesStorage()


def get_last_fragment():
    return _LaunchActivity.getLastFragment()


def run_on_queue(fn, queue_name=None, delay=0):
    """Runs fn on a background thread."""
    _Bridge.runOnBackground(R(fn), int(delay))


def send_text(peer_id, text, account=None):
    _Bridge.sendText(get_selected_account() if account is None else account, int(peer_id), str(text))


def send_message(params, account=None):
    """params: dict with at least "peer" and "message" (text)."""
    peer = params.get("peer")
    message = params.get("message")
    if peer is None or message is None:
        raise ValueError("send_message needs 'peer' and 'message'")
    send_text(peer, message, account)


class _Delegate(dynamic_proxy(_RequestDelegate)):
    def __init__(self, callback):
        super().__init__()
        self.callback = callback

    def run(self, response, error):
        if self.callback is not None:
            try:
                self.callback(response, error)
            except Exception as e:
                _Bridge.log("plugin", "Request callback failed: %r" % (e,))


def send_request(request, callback=None, account=None):
    return get_connections_manager(account).sendRequest(request, _Delegate(callback))

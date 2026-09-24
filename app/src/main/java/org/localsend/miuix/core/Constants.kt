package org.localsend.miuix.core

/**
 * 集中收敛的项目常量定义，包括持久化配置键、LocalSend 协议路由、Intent 动作与网络参数。
 */
object PreferenceKeys {
    const val PREF_NAME = "localsend_settings"
    const val KEY_ALIAS = "alias"
    const val KEY_PORT = "port"
    const val KEY_QUICK_SAVE = "quick_save"
    const val KEY_AUTO_COPY_TEXT = "auto_copy_text"
    const val KEY_SAVE_TO_HISTORY = "save_to_history"
    const val KEY_USE_HTTPS = "use_https"
    const val KEY_DEVICE_TYPE = "device_type"
    const val KEY_DOWNLOAD = "download"
    const val KEY_PIN = "pin"
    const val KEY_THEME = "theme_mode_index"
    const val KEY_TREE_URI = "download_tree_uri"
    const val KEY_DOWNLOAD_DISPLAY = "download_display"
    const val KEY_VIBRATE = "vibrate_on_complete"
    const val KEY_LAST_TAB = "last_selected_tab"
    const val KEY_RECENT_MANUAL_IPS = "recent_manual_ips"
    const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"
    const val KEY_IGNORED_VERSION = "ignored_version"
    const val KEY_PROMPTED_UPDATE_VERSION = "prompted_update_version"
    const val KEY_IS_OS3_EFFECT = "is_os3_effect"
    const val KEY_WIDE_SCREEN_NAVIGATION_RAIL = "wide_screen_navigation_rail"
    const val KEY_SAVE_TEXT_AS_FILE = "save_text_as_file"
    const val KEY_AUTO_CATEGORIZE_MEDIA = "auto_categorize_media"
}

object ExternalLinks {
    const val GITHUB_REPO = "https://github.com/137458/localsend-miuix"
    const val APACHE_LICENSE = "https://www.apache.org/licenses/LICENSE-2.0"
}

object LocalSendRoutes {
    const val WEB_ROOT = "/"
    const val INFO_V2 = "/api/localsend/v2/info"
    const val INFO_V1 = "/api/localsend/v1/info"
    const val REGISTER_V2 = "/api/localsend/v2/register"
    const val REGISTER_V1 = "/api/localsend/v1/register"
    const val PREPARE_UPLOAD = "/api/localsend/v2/prepare-upload"
    const val UPLOAD = "/api/localsend/v2/upload"
    const val CANCEL = "/api/localsend/v2/cancel"
    const val PREPARE_DOWNLOAD = "/api/localsend/v2/prepare-download"
    const val DOWNLOAD = "/api/localsend/v2/download"
    const val DOWNLOAD_ZIP = "/api/localsend/v2/download-zip"
}

/** 协议层固定提示文本：接收方主动取消时回传，发送方据此区分取消与令牌/权限拒绝。 */
object ProtocolMessages {
    const val CANCELED_BY_RECEIVER = "Transfer canceled by receiver"
}

object AppActions {
    const val ACTION_CANCEL_TRANSFER = "org.localsend.miuix.action.CANCEL_TRANSFER"
    const val ACTION_ACCEPT_TRANSFER = "org.localsend.miuix.action.ACCEPT_TRANSFER"
    const val ACTION_DECLINE_TRANSFER = "org.localsend.miuix.action.DECLINE_TRANSFER"
    const val EXTRA_SESSION_ID = "extra_session_id"
    const val ACTION_START_SERVICE = "org.localsend.miuix.service.ACTION_START"
    const val ACTION_STOP_SERVICE = "org.localsend.miuix.service.ACTION_STOP"
    const val EXTRA_SESSION_COUNT = "extra_session_count"
    const val EXTRA_INTENT_PROCESSED = "org.localsend.miuix.extra.INTENT_PROCESSED"
}

object NetworkConstants {
    const val DEFAULT_MULTICAST_IP = "224.0.0.167"
    const val DEFAULT_PORT = 53317
    const val PROTOCOL_VERSION = "2.1"
    const val MULTICAST_LOCK_TAG = "LocalSendMiuixMulticastLock"
    const val WAKE_LOCK_TAG = "LocalSend:TransferWakeLock"
    const val WIFI_LOCK_TAG = "LocalSend:TransferWifiLock"
}

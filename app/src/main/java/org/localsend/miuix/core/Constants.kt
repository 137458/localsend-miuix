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
    const val KEY_IS_OS3_EFFECT = "is_os3_effect"
    const val KEY_WIDE_SCREEN_NAVIGATION_RAIL = "wide_screen_navigation_rail"
    const val KEY_SAVE_TEXT_AS_FILE = "save_text_as_file"
    const val KEY_AUTO_CATEGORIZE_MEDIA = "auto_categorize_media"
    const val KEY_LANGUAGE = "language"
    const val KEY_QUICK_SAVE_FROM_FAVORITES = "quick_save_from_favorites"
    const val KEY_WEB_SHARE_AUTO_ACCEPT = "web_share_auto_accept"
    const val KEY_AUTO_FINISH = "auto_finish"
    const val KEY_NETWORK_WHITELIST = "network_whitelist"
    const val KEY_NETWORK_BLACKLIST = "network_blacklist"
    const val KEY_MULTICAST_GROUP = "multicast_group"
    const val KEY_DISCOVERY_TIMEOUT = "discovery_timeout"
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

    // 自定义扩展路由：超门槛文件校验通过后确认转正 / 不一致时丢弃临时文件；官方客户端不会调用
    const val CHECKSUM_COMMIT = "/api/localsend/v2/checksum-commit"
    const val CHECKSUM_REJECT = "/api/localsend/v2/checksum-reject"

    // 自定义扩展路由：查询本次文件的确认结果，供发送方在确认请求超时时区分“确实失败”与“状态未知”
    const val CHECKSUM_STATUS = "/api/localsend/v2/checksum-status"

    const val PREPARE_DOWNLOAD = "/api/localsend/v2/prepare-download"
    const val DOWNLOAD = "/api/localsend/v2/download"
    const val DOWNLOAD_ZIP = "/api/localsend/v2/download-zip"
}

/** 协议层固定提示文本：接收方主动取消时回传，发送方据此区分取消与令牌/权限拒绝。 */
object ProtocolMessages {
    const val CANCELED_BY_RECEIVER = "Transfer canceled by receiver"

    /** 用户显式拒绝（超时或点“拒绝”）：与主动取消共用 403，但文案必须可区分。 */
    const val DECLINED_BY_USER = "Transfer declined by user"
}

/**
 * 大文件完整性校验的加载式扩展：官方协议只在 prepare-upload 中提供可选 sha256，没有“接收端回报摘要”的字段。
 * 这里用自定义请求/响应头承载，不修改任何公开协议字段；对端不认识时按 HTTP 规范忽略，传输行为不受影响。
 */
object ChecksumHeaders {
    /** 上传请求头：值为 "1" 表示只要求回报摘要（旧版发送方），"2" 表示要求回报并暂存待确认。 */
    const val REQUEST = "X-Localsend-Miuix-Checksum"

    /** 请求头取值：只要求回报摘要，接收端立即转正（旧版发送方）。 */
    const val REQUEST_REPORT_ONLY = "1"

    /** 请求头取值：要求回报摘要，并先暂存等待发送方确认后再转正。 */
    const val REQUEST_DEFERRED = "2"

    /** 上传响应头：接收端回报实际落盘内容的小写十六进制 SHA-256，缺省即对端不支持回报。 */
    const val RESPONSE = "X-Localsend-Miuix-Sha256"

    /** 上传响应头：值为 "1" 表示接收端已写入临时位置，等待发送方确认摘要后才转正。 */
    const val PENDING = "X-Localsend-Miuix-Pending"

    /** 接收端临时文件的保鲜期：发送方未在时限内确认时自动丢弃，避免残留错误文件。 */
    const val PENDING_TTL_MS = 60_000L

    /**
     * 确认结果的保留时长：确认接口幂等可重放，发送方超时后可在此时限内查询到同一结果，
     * 超时未查询的墓碑记录自动清理，避免内存无界增长。
     */
    const val RESOLUTION_TTL_MS = 5 * 60_000L

    /** 状态查询响应头：committed / discarded / pending / unknown。 */
    const val STATE = "X-Localsend-Miuix-Checksum-State"
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
    const val DEFAULT_DISCOVERY_TIMEOUT_SECONDS = 5
    const val PROTOCOL_VERSION = "2.1"
    const val MULTICAST_LOCK_TAG = "LocalSendMiuixMulticastLock"
    const val WAKE_LOCK_TAG = "LocalSend:TransferWakeLock"
    const val WIFI_LOCK_TAG = "LocalSend:TransferWifiLock"
}

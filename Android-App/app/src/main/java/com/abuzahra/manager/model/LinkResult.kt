package com.abuzahra.manager.model

data class LinkResult(
    val ok: Boolean = false,
    val success: Boolean = false,
    val device_token: String? = null,
    val token: String? = null,
    val server_domain: String? = null,
    val message: String = "",
    val error: String = "",
    /**
     * HTTP status code from the server, when known. Set manually by
     * [com.abuzahra.manager.api.ApiClient] for endpoints where the status
     * code carries semantic meaning (e.g. restore_session returns 401 when
     * the device_token doesn't match and 404 when no previous session
     * exists). Defaults to null for responses where the code wasn't
     * captured.
     */
    val httpCode: Int? = null
)

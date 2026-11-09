package com.gecko.app.integrations

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

/**
 * Abre o login do Discord em um WebView e tenta capturar o token de sessão
 * depois que o usuário faz login. O token é devolvido via [Intent] extra
 * [EXTRA_TOKEN].
 *
 * Aviso: capturar token de usuário via WebView é frágil — o Discord costuma
 * marcar o token como HttpOnly, exigir 2FA/CAPTCHA e fazer fingerprint de
 * dispositivo. Se a extração automática falhar, o usuário precisa colar o
 * token manualmente nas configurações.
 */
class DiscordLoginActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TOKEN = "discord_token"
        const val REQUEST_CODE = 2001

        fun createIntent(context: android.content.Context): Intent =
            Intent(context, DiscordLoginActivity::class.java)
    }

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setWebViewClient(object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (url.contains("discord.com/app") || url.contains("discord.com/channels")) {
                        val token = extrairTokenDosCookies()
                        if (token.isNotBlank()) {
                            devolverToken(token)
                        } else {
                            injetarLeituraToken()
                        }
                    }
                }
            })
        }
        setContent(webView)
        webView.loadUrl("https://discord.com/login")
    }

    private fun extrairTokenDosCookies(): String {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie("https://discord.com") ?: ""
        for (cookie in cookies.split(";")) {
            val trimmed = cookie.trim()
            if (trimmed.startsWith("token=")) return trimmed.substringAfter("=")
            if (trimmed.startsWith("discord_token=")) return trimmed.substringAfter("=")
        }
        return ""
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun injetarLeituraToken() {
        val js = """
            (function() {
                try {
                    var token = localStorage.getItem('token');
                    if (token) {
                        document.cookie = "discord_token=" + token + "; domain=.discord.com; path=/;";
                    }
                } catch (e) {}
            })();
        """.trimIndent()
        webView.post { webView.evaluateJavascript(js, null) }
        webView.postDelayed({ devolverToken(extrairTokenDosCookies()) }, 1500)
    }

    private fun devolverToken(token: String) {
        val result = Intent().putExtra(EXTRA_TOKEN, token)
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}

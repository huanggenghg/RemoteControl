package com.lumostech.autoclick

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumostech.autoclick.ui.theme.AutoclickTheme

/** All information is bundled locally and can be read before granting permissions. */
class LocalInfoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val privacy = intent.getStringExtra(DOCUMENT) != GUIDE
        val title = if (privacy) "隐私与权限说明" else "使用指南"
        val raw = if (privacy) R.raw.privacy_policy else R.raw.user_guide
        val document = resources.openRawResource(raw).bufferedReader(Charsets.UTF_8).use { it.readText() }
        setContent {
            AutoclickTheme {
                val scroll = rememberScrollState()
                val publisher = remember { getString(R.string.autoclick_publisher) }
                val email = remember { getString(R.string.autoclick_support_email) }
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = ::finish) { Text("返回") }
                            TextButton(onClick = {
                                startActivity(intent(this@LocalInfoActivity, guide = privacy))
                                finish()
                            }) { Text(if (privacy) "使用指南" else "隐私与权限") }
                        }
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(title, style = MaterialTheme.typography.headlineMedium)
                            Text("AutoClick ${BuildConfig.VERSION_NAME} · 纯离线版",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (privacy) {
                                Text("开发者：$publisher\n反馈邮箱：$email",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(document, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val DOCUMENT = "document"
        private const val GUIDE = "guide"
        fun intent(context: Context, guide: Boolean = false): Intent =
            Intent(context, LocalInfoActivity::class.java).putExtra(DOCUMENT, if (guide) GUIDE else "privacy")
    }
}

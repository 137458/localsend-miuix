package org.localsend.miuix.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle

/** 设置类页面的「分区标题 + 卡片」容器，统一卡片横向 12dp 边距。 */
@Composable
fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    SmallTitle(text = title)
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        content()
    }
}

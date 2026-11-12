package app.lumentv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lumentv.data.EpgFailure
import app.lumentv.data.LoadState
import app.lumentv.ui.theme.Brand

/** Lists guide files that failed to download, with a Retry button (only those files are fetched again). */
@Composable
fun GuideFailuresCard(failures: List<EpgFailure>, state: LoadState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (failures.isEmpty()) return
    val loading = state is LoadState.Loading
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = Brand.Live.copy(alpha = 0.12f)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = Brand.Live, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (failures.size == 1) "1 guide file couldn't be downloaded" else "${failures.size} guide files couldn't be downloaded",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            failures.forEach { f ->
                Text(
                    "• ${f.name}: ${f.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                )
            }
            Row(Modifier.padding(start = 28.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onRetry, enabled = !loading) {
                    if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (loading) "Retrying…" else if (failures.size == 1) "Retry" else "Retry failed files")
                }
                if (loading) {
                    Text(
                        (state as LoadState.Loading).label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

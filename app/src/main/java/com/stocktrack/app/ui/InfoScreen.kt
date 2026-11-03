package com.stocktrack.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stocktrack.app.BuildConfig
import com.stocktrack.app.R

/** Everyone: which version of the app this is, when it was built and by whom. */
@Composable
fun InfoScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Panel(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(Modifier.padding(24.dp)) {
                Image(
                    bitmap = ImageBitmap.imageResource(R.drawable.logo_miclinic),
                    contentDescription = "Mi' clinic",
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .width(280.dp)
                        .aspectRatio(1200f / 165f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = DentTrackColors.PrimaryDark
                )
                Spacer(Modifier.height(12.dp))
                InfoRow(stringResource(R.string.info_version), BuildConfig.VERSION_NAME)
                InfoRow(stringResource(R.string.info_build), BuildConfig.VERSION_CODE.toString())
                InfoRow(stringResource(R.string.info_build_date), BuildConfig.BUILD_DATE)
                InfoRow(stringResource(R.string.info_built_by), "Arvydas Taraila")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

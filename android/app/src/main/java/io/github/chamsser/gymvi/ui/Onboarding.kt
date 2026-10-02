package io.github.chamsser.gymvi.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R
import kotlinx.coroutines.launch

private const val OnboardingPrefs = "gymvi_onboarding"
private const val OnboardingDoneKey = "completed_v1"

internal fun onboardingCompleted(context: Context): Boolean =
    context.getSharedPreferences(OnboardingPrefs, Context.MODE_PRIVATE).getBoolean(OnboardingDoneKey, false)

internal fun markOnboardingCompleted(context: Context) {
    context.getSharedPreferences(OnboardingPrefs, Context.MODE_PRIVATE).edit().putBoolean(OnboardingDoneKey, true).apply()
}

private data class OnboardingPage(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val body: Int)

private val OnboardingPages = listOf(
    OnboardingPage(R.drawable.ic_material_symbol_chat_24, R.string.onboarding_ai_title, R.string.onboarding_ai_body),
    OnboardingPage(R.drawable.ic_material_symbol_map_24, R.string.onboarding_map_title, R.string.onboarding_map_body),
    OnboardingPage(R.drawable.ic_material_symbol_person_24, R.string.onboarding_my_title, R.string.onboarding_my_body),
    OnboardingPage(R.drawable.ic_material_symbol_my_location_24, R.string.onboarding_location_title, R.string.onboarding_location_body),
)

/**
 * The first-run introduction over the app: three pages on what Gymvi does, then the location
 * request. Either answer to the request, or 나중에 하기, finishes it for good; the app stays usable
 * without location.
 */
@Composable
internal fun GymviOnboarding(
    onRequestLocation: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(pageCount = { OnboardingPages.size })
    val scope = rememberCoroutineScope()
    val last = OnboardingPages.lastIndex
    val onLast = pager.currentPage == last
    BackHandler(enabled = pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
    Surface(modifier = modifier.fillMaxSize().testTag("onboarding"), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp)) {
                if (!onLast) {
                    TextButton(
                        onClick = { scope.launch { pager.animateScrollToPage(last) } },
                        modifier = Modifier.align(Alignment.CenterEnd).testTag("onboarding-skip"),
                    ) { Text(stringResource(R.string.onboarding_skip)) }
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
                OnboardingPageContent(OnboardingPages[index], testTag = "onboarding-page-$index")
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                OnboardingPages.indices.forEach { index ->
                    val selected = index == pager.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (selected) 20.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    )
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                Button(
                    onClick = {
                        if (onLast) {
                            onRequestLocation()
                            onFinish()
                        } else {
                            scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("onboarding-next"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = stringResource(if (onLast) R.string.onboarding_allow_location else R.string.onboarding_next),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 4.dp)) {
                    if (onLast) {
                        TextButton(
                            onClick = onFinish,
                            modifier = Modifier.align(Alignment.Center).testTag("onboarding-later"),
                        ) { Text(stringResource(R.string.onboarding_later)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage, testTag: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp).testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(page.icon),
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = stringResource(page.title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(page.body),
            modifier = Modifier.width(320.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge.merge(CombinedMyDescriptionBreak),
            textAlign = TextAlign.Center,
        )
    }
}

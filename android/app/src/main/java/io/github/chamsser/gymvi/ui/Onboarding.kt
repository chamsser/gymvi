package io.github.chamsser.gymvi.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.AiAgeBand
import io.github.chamsser.gymvi.data.AiExerciseExperience
import io.github.chamsser.gymvi.data.AiExerciseGoal
import io.github.chamsser.gymvi.data.AiLocalProfile
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

/** Where the first run is: the introduction pages, the 신체 정보 offer, then its optional steps. */
private enum class OnboardingStage { INTRO, BODY_OFFER, MEASURE, AGE, EXPERIENCE, GOAL }

/**
 * The first-run introduction over the app: three pages on what Gymvi does, the location request,
 * then an offer to fill in 신체 정보 step by step. Every step is optional; finishing or declining at
 * any point ends the introduction for good, and the app stays usable without location.
 */
@Composable
internal fun GymviOnboarding(
    onRequestLocation: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    profile: AiLocalProfile = AiLocalProfile(),
    onProfileSaved: (AiLocalProfile) -> Unit = {},
) {
    var stage by rememberSaveable { mutableStateOf(OnboardingStage.INTRO) }
    when (stage) {
        OnboardingStage.INTRO -> OnboardingIntro(
            onRequestLocation = onRequestLocation,
            onDone = { stage = OnboardingStage.BODY_OFFER },
            modifier = modifier,
        )
        OnboardingStage.BODY_OFFER -> OnboardingBodyOffer(
            onStart = { stage = OnboardingStage.MEASURE },
            onLater = onFinish,
            modifier = modifier,
        )
        else -> OnboardingBodySteps(
            stage = stage,
            onStageChange = { stage = it },
            profile = profile,
            onDone = { changed ->
                onProfileSaved(changed)
                onFinish()
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun OnboardingIntro(
    onRequestLocation: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
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
                            onDone()
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
                            onClick = onDone,
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

/** The offer after the introduction: fill in 신체 정보 now, or later from 내 정보. */
@Composable
private fun OnboardingBodyOffer(onStart: () -> Unit, onLater: () -> Unit, modifier: Modifier) {
    Surface(modifier = modifier.fillMaxSize().testTag("onboarding-body-offer"), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                OnboardingPageContent(
                    OnboardingPage(R.drawable.ic_material_symbol_person_24, R.string.onboarding_body_title, R.string.onboarding_body_body),
                    testTag = "onboarding-body-offer-page",
                )
            }
            OnboardingActions(
                primary = stringResource(R.string.onboarding_body_start),
                onPrimary = onStart,
                primaryTag = "onboarding-body-start",
                secondary = stringResource(R.string.onboarding_later),
                onSecondary = onLater,
                secondaryTag = "onboarding-body-later",
            )
        }
    }
}

@Composable
private fun OnboardingActions(
    primary: String,
    onPrimary: () -> Unit,
    primaryTag: String,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    secondaryTag: String = "",
    primaryEnabled: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp)) {
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag(primaryTag),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(text = primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (secondary != null) {
            TextButton(
                onClick = onSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp).heightIn(min = 48.dp).testTag(secondaryTag),
            ) { Text(secondary) }
        }
    }
}

private val BodySteps = listOf(OnboardingStage.MEASURE, OnboardingStage.AGE, OnboardingStage.EXPERIENCE, OnboardingStage.GOAL)

/**
 * 키 and 몸무게 on one page, then one question per page for 연령대, 운동 경험 and 운동 목표. Each can
 * be left empty. The page pads for the keyboard in the app window, so the 다음 button rises with
 * the keyboard instead of after it.
 */
@Composable
private fun OnboardingBodySteps(
    stage: OnboardingStage,
    onStageChange: (OnboardingStage) -> Unit,
    profile: AiLocalProfile,
    onDone: (AiLocalProfile) -> Unit,
    modifier: Modifier,
) {
    var height by rememberSaveable { mutableStateOf(profile.heightCm?.toString().orEmpty()) }
    var weight by rememberSaveable { mutableStateOf(profile.weightKg?.let(::aiDecimalText).orEmpty()) }
    var age by rememberSaveable { mutableStateOf(profile.ageBand) }
    var experience by rememberSaveable { mutableStateOf(profile.experience) }
    var goal by rememberSaveable { mutableStateOf(profile.goal) }
    var revealErrors by rememberSaveable { mutableStateOf(false) }
    val heightValid = height.isBlank() || aiProfileHeightOrNull(height) != null
    val weightValid = weight.isBlank() || aiProfileWeightOrNull(weight) != null
    val index = BodySteps.indexOf(stage)
    val focusManager = LocalFocusManager.current
    BackHandler { onStageChange(if (index == 0) OnboardingStage.BODY_OFFER else BodySteps[index - 1]) }
    val next: () -> Unit = {
        if (stage == OnboardingStage.MEASURE && !(heightValid && weightValid)) {
            revealErrors = true
        } else if (index == BodySteps.lastIndex) {
            onDone(
                profile.copy(
                    heightCm = aiProfileHeightOrNull(height),
                    weightKg = aiProfileWeightOrNull(weight),
                    ageBand = age,
                    experience = experience,
                    goal = goal,
                ),
            )
        } else {
            focusManager.clearFocus()
            onStageChange(BodySteps[index + 1])
        }
    }
    Surface(modifier = modifier.fillMaxSize().testTag("onboarding-body"), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Text(
                text = stringResource(R.string.onboarding_body_step, index + 1, BodySteps.size),
                modifier = Modifier.padding(start = 24.dp, top = 20.dp).testTag("onboarding-body-step"),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    text = stringResource(
                        when (stage) {
                            OnboardingStage.AGE -> R.string.onboarding_body_age_title
                            OnboardingStage.EXPERIENCE -> R.string.onboarding_body_experience_title
                            OnboardingStage.GOAL -> R.string.onboarding_body_goal_title
                            else -> R.string.onboarding_body_measure_title
                        },
                    ),
                    modifier = Modifier.padding(top = 12.dp).semantics { heading() }.testTag("onboarding-body-title"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.onboarding_body_optional),
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                when (stage) {
                    OnboardingStage.AGE -> OnboardingChoices(AiAgeBand.entries, age, { stringResource(it.labelRes) }, "onboarding-age") { age = it }
                    OnboardingStage.EXPERIENCE -> OnboardingChoices(AiExerciseExperience.entries, experience, { stringResource(it.labelRes) }, "onboarding-experience") { experience = it }
                    OnboardingStage.GOAL -> OnboardingChoices(AiExerciseGoal.entries, goal, { stringResource(it.labelRes) }, "onboarding-goal") { goal = it }
                    else -> {
                        val heightFocus = remember { FocusRequester() }
                        OnboardingNumberField(
                            label = stringResource(R.string.my_profile_height),
                            value = height,
                            onValueChange = { height = aiDigitsInput(it, maxDigits = 3) },
                            unit = stringResource(R.string.my_profile_height_unit),
                            error = stringResource(R.string.my_profile_height_error).takeIf { revealErrors && !heightValid },
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                            onIme = { focusManager.moveFocus(FocusDirection.Down) },
                            testTag = "onboarding-height",
                            modifier = Modifier.focusRequester(heightFocus),
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        OnboardingNumberField(
                            label = stringResource(R.string.my_profile_weight),
                            value = weight,
                            onValueChange = { weight = aiDecimalInput(it, integerDigits = 3, fractionDigits = 2) },
                            unit = stringResource(R.string.my_profile_weight_unit),
                            error = stringResource(R.string.my_profile_weight_error).takeIf { revealErrors && !weightValid },
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                            onIme = next,
                            testTag = "onboarding-weight",
                        )
                        LaunchedEffect(Unit) { heightFocus.requestFocus() }
                    }
                }
            }
            OnboardingActions(
                primary = stringResource(if (index == BodySteps.lastIndex) R.string.onboarding_done else R.string.onboarding_next),
                onPrimary = next,
                primaryTag = "onboarding-body-next",
            )
        }
    }
}

@Composable
private fun OnboardingNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    error: String?,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onIme: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier.weight(1f).semantics { contentDescription = label }.testTag("$testTag-input"),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(onNext = { onIme() }, onDone = { onIme() }),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
            Text(
                text = unit,
                modifier = Modifier.padding(start = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        )
        Text(
            text = error.orEmpty(),
            modifier = Modifier.padding(top = 6.dp).heightIn(min = 20.dp).testTag("$testTag-error"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** One answer of a question; tapping the chosen one again clears it, since every answer is optional. */
@Composable
private fun <T : Any> OnboardingChoices(
    choices: List<T>,
    selected: T?,
    labelOf: @Composable (T) -> String,
    testTag: String,
    onSelect: (T?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        choices.forEach { choice ->
            val chosen = choice == selected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .selectable(selected = chosen, role = Role.RadioButton) { onSelect(if (chosen) null else choice) }
                    .testTag("$testTag-${(choice as Enum<*>).name}")
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = labelOf(choice),
                    color = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

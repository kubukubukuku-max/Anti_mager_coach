package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.signOutGoogleAccount
import com.example.data.FirebaseRepository
import com.example.data.GeminiApiKeyStore
import com.example.data.WorkoutRepository
import com.example.health.HealthConnectManager
import com.example.ui.AuthScreen
import com.example.ui.CoachScreen
import com.example.ui.CoachViewModel
import com.example.ui.theme.AntiMagerCoachTheme
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val localRepository by lazy { WorkoutRepository(applicationContext) }
    private val firebaseRepository: FirebaseRepository? by lazy {
        FirebaseRepository.createOrNull(applicationContext)
    }
    private val healthConnectManager by lazy { HealthConnectManager(applicationContext) }
    private val reminderScheduler by lazy { ReminderScheduler(applicationContext) }
    private val geminiApiKeyStore by lazy { GeminiApiKeyStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure IMPORTANCE_HIGH reminder notification channel exists on app launch
        NotificationHelper.createNotificationChannel(applicationContext)

        val openedForHealthRationale = intent?.action == ACTION_SHOW_PERMISSIONS_RATIONALE ||
            intent?.action == ACTION_VIEW_PERMISSION_USAGE

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            val localThemeMode by localRepository.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = AppThemeMode.WARM_CREAM
            )
            val appScope = rememberCoroutineScope()
            var showRationaleDialog by remember { mutableStateOf(openedForHealthRationale) }

            LaunchedEffect(localThemeMode) {
                val barStyle = if (localThemeMode.isDark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            AppNavigation(
                localRepository = localRepository,
                firebaseRepository = firebaseRepository,
                healthConnectManager = healthConnectManager,
                reminderScheduler = reminderScheduler,
                geminiApiKeyStore = geminiApiKeyStore,
                localThemeMode = localThemeMode,
                onLocalThemeChange = { mode ->
                    appScope.launch { localRepository.setThemeMode(mode) }
                }
            )

            if (showRationaleDialog) {
                AntiMagerCoachTheme(
                    themeMode = localThemeMode,
                    onThemeModeChange = { mode ->
                        appScope.launch { localRepository.setThemeMode(mode) }
                    }
                ) {
                    AlertDialog(
                        onDismissRequest = { showRationaleDialog = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(20.dp),
                        title = {
                            Text(
                                text = stringResource(R.string.hc_rationale_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.hc_rationale_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showRationaleDialog = false }) {
                                Text("Mengerti")
                            }
                        }
                    )
                }
            }
        }
    }

    companion object {
        private const val ACTION_SHOW_PERMISSIONS_RATIONALE =
            "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"
        private const val ACTION_VIEW_PERMISSION_USAGE =
            "android.intent.action.VIEW_PERMISSION_USAGE"
    }
}

@Composable
private fun AppNavigation(
    localRepository: WorkoutRepository,
    firebaseRepository: FirebaseRepository?,
    healthConnectManager: HealthConnectManager,
    reminderScheduler: ReminderScheduler,
    geminiApiKeyStore: GeminiApiKeyStore,
    localThemeMode: AppThemeMode,
    onLocalThemeChange: (AppThemeMode) -> Unit
) {
    // If Firebase is not configured (e.g. APK built without google-services.json),
    // skip the login gate and launch directly into CoachScreen in Local Mode.
    if (firebaseRepository == null) {
        key("local_mode") {
            val viewModel: CoachViewModel = viewModel(
                key = "local_mode",
                factory = viewModelFactory {
                    initializer {
                        CoachViewModel(
                            currentUserId = null,
                            userDisplayName = null,
                            userEmail = null,
                            localRepository = localRepository,
                            firebaseRepository = null,
                            healthConnectManager = healthConnectManager,
                            reminderScheduler = reminderScheduler,
                            geminiApiKeyStore = geminiApiKeyStore
                        )
                    }
                }
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            AntiMagerCoachTheme(
                themeMode = uiState.themeMode,
                onThemeModeChange = viewModel::setThemeMode
            ) {
                CoachScreen(
                    viewModel = viewModel,
                    onSignOut = {}
                )
            }
        }
        return
    }

    val currentUser by firebaseRepository.observeAuthState().collectAsStateWithLifecycle(
        initialValue = firebaseRepository.currentUser
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) {
        try {
            CredentialManager.create(context)
        } catch (_: Throwable) {
            null
        }
    }
    var skipAutoSignIn by remember { mutableStateOf(false) }

    val user = currentUser
    if (user == null) {
        // Unauthenticated session: Render AuthScreen only.
        // CoachViewModel and Firestore listeners are NOT instantiated here.
        AntiMagerCoachTheme(
            themeMode = localThemeMode,
            onThemeModeChange = onLocalThemeChange
        ) {
            AuthScreen(
                firebaseRepository = firebaseRepository,
                skipAutoSignIn = skipAutoSignIn,
                onAuthSuccess = { skipAutoSignIn = false }
            )
        }
    } else {
        // Authenticated session: Key composition and ViewModel by uid so signing out
        // or switching accounts disposes old Firestore listeners cleanly.
        val uid = user.uid
        key(uid) {
            val viewModel: CoachViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        CoachViewModel(
                            currentUserId = uid,
                            userDisplayName = user.displayName,
                            userEmail = user.email,
                            localRepository = localRepository,
                            firebaseRepository = firebaseRepository,
                            healthConnectManager = healthConnectManager,
                            reminderScheduler = reminderScheduler,
                            geminiApiKeyStore = geminiApiKeyStore
                        )
                    }
                }
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            AntiMagerCoachTheme(
                themeMode = uiState.themeMode,
                onThemeModeChange = viewModel::setThemeMode
            ) {
                CoachScreen(
                    viewModel = viewModel,
                    onSignOut = {
                        skipAutoSignIn = true
                        val manager = credentialManager
                        if (manager != null) {
                            signOutGoogleAccount(
                                credentialManager = manager,
                                firebaseRepository = firebaseRepository,
                                scope = scope
                            )
                        } else {
                            firebaseRepository.signOut()
                        }
                    }
                )
            }
        }
    }
}

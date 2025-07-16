package com.georgesak.customapplauncher

import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings // Import for Settings intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.georgesak.customapplauncher.ui.theme.CustomAppLauncherTheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap // Import for toBitmap()
import androidx.compose.runtime.* // Import for remember and mutableStateOf
import androidx.compose.foundation.background // Import for background modifier
import com.georgesak.customapplauncher.ui.theme.SelectedBlue // Import the new color
import androidx.compose.ui.graphics.Color // Import Color
import androidx.compose.material3.OutlinedTextField // Import OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api // Import ExperimentalMaterial3Api
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
 
 class MainActivity : ComponentActivity() {
     override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                createNotificationChannel()
                showAppPickerNotification()
            }
        } else {
            // For older Android versions, permission is granted at install time
            createNotificationChannel()
            showAppPickerNotification()
        }

        val shouldShowAppPicker = intent.getBooleanExtra("show_app_picker", false)

        val sharedPref = getPreferences(android.content.Context.MODE_PRIVATE)
        val lastLaunchedAppPackage = sharedPref.getString("last_launched_app", null)

        if (!shouldShowAppPicker && lastLaunchedAppPackage != null) {
            val launchIntent = packageManager.getLaunchIntentForPackage(lastLaunchedAppPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(launchIntent)
                finish() // Close the launcher activity
                return // Stop further execution
            }
        }

        setContent {
            CustomAppLauncherTheme {
                AppListScreen { packageName ->
                    with(sharedPref.edit()) {
                        putString("last_launched_app", packageName)
                        apply()
                    }
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                        // Do not finish here, keep the launcher in the background
                    }
                }
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                createNotificationChannel()
                showAppPickerNotification()
            } else {
                // Handle the case where the user denies the permission
                // You might want to show a message or disable the notification feature
            }
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "App Picker"
            val descriptionText = "Notification to open the app picker"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("app_picker_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showAppPickerNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_app_picker", true) // Add an extra to indicate launching from notification
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(this, "app_picker_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Revert to a monochromatic icon
            .setContentTitle("App Launcher")
            .setContentText("Tap to open app picker")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Makes the notification persistent

        with(androidx.core.app.NotificationManagerCompat.from(this)) {
            notify(1, builder.build())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppListScreen(onAppClick: (String) -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val packageManager = context.packageManager
    val sharedPref = context.getSharedPreferences("app_launcher_prefs", Context.MODE_PRIVATE)
    var selectedPackageName by remember { mutableStateOf(sharedPref.getString("last_launched_app", null)) }

    val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { app ->
            app.packageName != context.packageName &&
            packageManager.getLaunchIntentForPackage(app.packageName) != null
        } // Filter out the current app and apps without a launcher activity
        .sortedWith(compareBy<ApplicationInfo> { it.packageName != selectedPackageName }
            .thenBy { packageManager.getApplicationLabel(it).toString() })
    var filterText by remember { mutableStateOf("") } // State for the filter text

    val filteredApps = apps.filter { app ->
        packageManager.getApplicationLabel(app).toString().contains(filterText, ignoreCase = true)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) { // Use Column to arrange button, filter, and list vertically
            Button(
                onClick = {
                    val settingsIntent = Intent(Settings.ACTION_HOME_SETTINGS)
                    context.startActivity(settingsIntent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Change Home Launcher")
            }

            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                label = { Text("Filter Apps") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) { // Use weight to make LazyColumn take remaining space
                items(filteredApps) { app -> // Use filteredApps here
                    val isSelected = app.packageName == selectedPackageName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    selectedPackageName = app.packageName // Update selected app
                                    with(sharedPref.edit()) {
                                        putString("last_launched_app", app.packageName)
                                        apply()
                                    }
                                    onAppClick(app.packageName)
                                },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val launchIntent =
                                        packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    }
                                }
                            )
                            .background(if (isSelected) SelectedBlue else Color.Transparent) // Apply background
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = packageManager.getApplicationIcon(app)
                        Image(
                            bitmap = icon.toBitmap().asImageBitmap(),
                            contentDescription = packageManager.getApplicationLabel(app).toString(),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = packageManager.getApplicationLabel(app).toString())
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    CustomAppLauncherTheme {
        AppListScreen {} // Empty lambda for preview
    }
}
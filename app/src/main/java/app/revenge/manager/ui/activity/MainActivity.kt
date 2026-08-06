package app.revenge.manager.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.revenge.manager.R
import app.revenge.manager.esharq.EsharqAuth
import app.revenge.manager.ui.screen.home.HomeScreen
import app.revenge.manager.ui.screen.installer.InstallerScreen
import app.revenge.manager.ui.theme.RevengeManagerTheme
import app.revenge.manager.utils.DiscordVersion
import app.revenge.manager.utils.Intents
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val esharqAuth: EsharqAuth by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val version = intent.getStringExtra(Intents.Extras.VERSION)

        handleSignInCallback(intent)

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf("android.permission.POST_NOTIFICATIONS"),
                0
            )
        }

        val screen = if (intent.action == Intents.Actions.INSTALL && version != null) {
            InstallerScreen(DiscordVersion.fromVersionCode(version)!!)
        } else {
            HomeScreen()
        }

        setContent {
            RevengeManagerTheme {
                Navigator(screen) {
                    SlideTransition(it)
                }
            }
        }
    }

    // The activity is singleTask, so returning from the browser arrives here rather than through
    // onCreate. Without this the receipt would be handed back to an instance that never reads it.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSignInCallback(intent)
    }

    /**
     * Reads the receipt esharq.org handed back, and says out loud what happened. "Not a member" is
     * the only outcome the user can act on, so it is the only one that offers a way forward.
     */
    private fun handleSignInCallback(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme != "esharq" || uri.host != "mobile-auth") return

        when (val result = esharqAuth.completeSignIn(uri)) {
            is EsharqAuth.Result.Success ->
                Toast.makeText(this, R.string.esharq_signed_in, Toast.LENGTH_LONG).show()

            is EsharqAuth.Result.NotMember -> {
                Toast.makeText(this, R.string.esharq_not_member, Toast.LENGTH_LONG).show()
                result.invite?.let {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                }
            }

            is EsharqAuth.Result.Failed ->
                Toast.makeText(this, R.string.esharq_sign_in_failed, Toast.LENGTH_LONG).show()
        }
    }
}

package com.example.syncedn

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.PathParser
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation

@Suppress("ConstPropertyName")
object UI {
    const val calendarButton = 1000
    const val chatsButton = 1001
    const val continueButton = 1002
    const val createTeamButton = 1003
    const val createTeamChoiceButton = 1004
    const val forgotPasswordButton = 1005
    const val getStartedButton = 1006
    const val googleButton = 1007
    const val homeTab = 1008
    const val joinTeamButton = 1009
    const val joinTeamChoiceButton = 1010
    const val loginButton = 1011
    const val loginEmail = 1012
    const val loginPassword = 1013
    const val loginPasswordToggle = 1014
    const val notificationsButton = 1015
    const val openRegisterButton = 1016
    const val otpCode = 1017
    const val profileButton = 1018
    const val registerButton = 1019
    const val registerConfirm = 1020
    const val registerConfirmToggle = 1021
    const val registerEmail = 1022
    const val registerName = 1023
    const val registerPassword = 1024
    const val registerPasswordToggle = 1025
    const val resetConfirm = 1026
    const val resetConfirmToggle = 1027
    const val resetEmail = 1028
    const val resetPassword = 1029
    const val resetPasswordToggle = 1030
    const val scheduleCard = 1031
    const val scheduleTab = 1032
    const val sendCodeButton = 1034
    const val settingsTab = 1035
    const val songsButton = 1036
    const val successMessage = 1037
    const val teamCode = 1038
    const val teamHubButton = 1039
    const val teamName = 1040
    const val teamsTab = 1041
    const val verifyOtpButton = 1042
    const val welcomeText = 1043
    const val worshipCard = 1044

    const val announcementsButton = 1045
    const val teamTitle = 1046
    const val pageBack = 1047
    const val settingsAvatar = 1048
    const val openProfileButton = 1049
    const val settingsTeam = 1050
    const val notificationSwitch = 1051
    const val logoutButton = 1052
    const val aboutButton = 1053
    const val helpButton = 1054
    const val profileEmail = 1055
    const val profileAvatar = 1056
    const val changePhoto = 1057
    const val profileName = 1058
    const val profileUsername = 1059
    const val profilePhone = 1060
    const val countrySpinner = 1061
    const val languageSpinner = 1062
    const val saveProfileButton = 1063
}

@Suppress("ViewConstructor")
class CornerArt(context: Context, private val flip: Boolean) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.withScale(width / 210f, height / 210f) {
            if (flip) { translate(210f, 0f); scale(-1f, 1f) }
            paint.color = Color.rgb(80, 5, 104); drawCircle(110f, -22f, 95f, paint)
            paint.color = Color.rgb(73, 10, 53); drawCircle(18f, 24f, 104f, paint)
            paint.color = Color.rgb(158, 6, 159); drawCircle(-40f, 141f, 103f, paint)
        }
    }
}

class EyeDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(21, 22, 49); style = Paint.Style.STROKE; strokeWidth = 1.6f }
    override fun draw(canvas: Canvas) {
        canvas.withTranslation(bounds.left.toFloat(), bounds.top.toFloat()) {
            scale(bounds.width() / 24f, bounds.height() / 24f)
            val path = PathParser.createPathFromPathData("M1,12 C6,3 18,3 23,12 M7,12 A5,5 0,1 0,17 12 A5,5 0,1 0,7 12")
            drawPath(path, paint)
        }
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

@Suppress("ViewConstructor")
class Symbol(context: Context, name: String, color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.6f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = PathParser.createPathFromPathData(when (name) {
        "back" -> "M16,3 L6,12 L16,21"
        "camera" -> "M3,6 L8,6 L10,3 L15,3 L17,6 L22,6 L22,21 L3,21 Z M8,13 A4,4 0,1 1,16 13 A4,4 0,1 1,8 13"
        "logout" -> "M4,2 L16,2 L16,8 M16,16 L16,22 L4,22 L4,16 M1,12 L13,12 M8,7 L13,12 L8,17"
        "about" -> "M2,12 A10,10 0,1 1,22 12 A10,10 0,1 1,2 12 M12,10 L12,18 M12,6 L12,7"
        "help" -> "M2,12 A10,10 0,1 1,22 12 A10,10 0,1 1,2 12 M8,8 C8,3 18,4 15,10 L12,13 L12,15 M12,18 L12,19"
        "cross", "add" -> "M12,1 L12,23 M1,12 L23,12"
        "announcement" -> "M3,8 L9,8 L21,3 L21,20 L9,15 L3,15 Z M6,15 L8,22 L12,22 L10,16"
        "mail" -> "M3,5 L21,5 L21,19 L3,19 Z M3,6 L12,13 L21,6"
        "lock" -> "M7,10 L7,7 C7,0 17,0 17,7 L17,10 M6,10 L18,10 L18,21 L6,21 Z M12,14 L12,17"
        "calendar" -> "M3,5 L21,5 L21,22 L3,22 Z M3,10 L21,10 M7,2 L7,7 M17,2 L17,7"
        "chat" -> "M3,3 L21,3 L21,19 L8,19 L3,23 Z"
        "music" -> "M9,18 L9,5 L20,2 L20,15 M9,18 A3,3 0,1 1,3 18 A3,3 0,1 1,9 18 M20,15 A3,3 0,1 1,14 15 A3,3 0,1 1,20 15"
        "teams" -> "M9,3 A4,4 0,1 1,9 11 A4,4 0,1 1,9 3 M3,22 L3,18 C3,12 15,12 15,18 L15,22 M17,3 C23,4 23,10 18,11 M18,14 C23,14 23,18 23,22"
        "user", "person" -> "M12,4 A4,4 0,1 1,12 12 A4,4 0,1 1,12 4 Z M4,20 C4,16 8,15 12,15 C16,15 20,16 20,20"
        "photo" -> "M2,3 L22,3 L22,21 L2,21 Z M3,18 L8,12 L13,17 L18,10 L22,16 M8,6 A2,2 0,1 1,8 10 A2,2 0,1 1,8 6"
        "warning" -> "M12,2 L23,22 L1,22 Z M12,8 L12,14 M12,18 L12,19"
        "bell" -> "M4,18 L6,15 L6,9 C6,1 18,1 18,9 L18,15 L20,18 Z M10,21 L14,21"
        "arrow" -> "M9,5 L16,12 L9,19"
        "home" -> "M2,11 L12,2 L22,11 M5,9 L5,22 L10,22 L10,15 L14,15 L14,22 L19,22 L19,9"
        "settings" -> "M2,12 A10,10 0,1 1,22 12 A10,10 0,1 1,2 12 M6,8 L18,8 M6,12 L18,12 M6,16 L18,16"
        else -> "M4,12 A8,8 0,1 1,20 12 M20,12 L20,7 M20,12 L15,12 M4,16 L5,18 M7,20 L9,21 M12,21 L14,21 M17,20 L19,18"
    })
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.withScale(width / 24f, height / 24f) {
            drawPath(path, paint)
        }
    }
}

object Art {
    fun data(name: String): String {
        val asset = ArtData.get(name)
        return if (asset.isNotEmpty()) asset else ArtData.get("profile_photo")
    }
}

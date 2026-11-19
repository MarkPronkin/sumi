package dev.cinema.crunchyroll.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cinema.crunchyroll.data.MotionLevel
import dev.cinema.crunchyroll.data.account.AccountState
import dev.cinema.crunchyroll.design.*

@Composable
internal fun LoginForm(vm: CinemaViewModel) {
    val state by vm.account.collectAsStateWithLifecycle()
    val busy=state is AccountState.SigningIn
    val error=(state as? AccountState.SignedOut)?.message
    var email by rememberSaveable {mutableStateOf("")}
    val loginOpen by vm.loginOpen.collectAsStateWithLifecycle()
    // Intentionally not saveable: passwords are never written to bundles, disk, or ViewModel state.
    var password by remember(loginOpen) {mutableStateOf("")}
    var visible by remember(loginOpen) {mutableStateOf(false)}
    var validation by remember {mutableStateOf<String?>(null)}
    val keyboard=LocalSoftwareKeyboardController.current
    val context=LocalContext.current
    val motion=LocalMotion.current
    val shake=remember {Animatable(0f)}
    LaunchedEffect(error) {
        if(error!=null && motion!=MotionLevel.Reduced) shake.animateTo(0f,keyframes {durationMillis=280;0f at 0;-10f at 55;8f at 120;-4f at 190;0f at 280})
    }
    fun submit() {
        if(busy) return
        if(email.isBlank() || !email.contains('@') || password.isEmpty()) {validation="Enter your account email and password.";return}
        keyboard?.hide();validation=null
        val input=password;password="";visible=false
        vm.login(email,input)
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Surface, unfocusedContainerColor = Surface, disabledContainerColor = Surface,
    )
    Column(Modifier.fillMaxWidth().graphicsLayer {translationX=shake.value}, verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Sign in", modifier = Modifier.semantics { heading() }, fontFamily=DisplayFont,
            fontSize=48.sp, lineHeight=50.sp, fontWeight=FontWeight.Bold)
        OutlinedTextField(email,{email=it;validation=null},modifier=Modifier.fillMaxWidth().semantics {contentDescription="Account email"},label={Text("Email")},singleLine=true,enabled=!busy,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email,imeAction=ImeAction.Next),colors=fieldColors,shape=RoundedCornerShape(12.dp))
        OutlinedTextField(password,{password=it;validation=null},modifier=Modifier.fillMaxWidth().semantics {contentDescription="Account password"},label={Text("Password")},singleLine=true,enabled=!busy,
            visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={submit()}),
            trailingIcon={TextButton(onClick={visible=!visible},enabled=!busy){Text(if(visible) "Hide" else "Show",color=Orange)}},colors=fieldColors,shape=RoundedCornerShape(12.dp))
        (validation ?: error)?.let {message -> Text(message,color=Orange,fontSize=14.sp,modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})}
        if(busy) Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp),color=Orange,strokeWidth=2.dp);Text("Signing in securely…",color=Muted)
        }
        Action(if(busy) "Signing in…" else "Sign in to Crunchyroll",Glyph.Profile,filled=true,enabled=!busy,modifier=Modifier.fillMaxWidth(),onClick=::submit)
        if(busy) Action("Cancel sign-in",modifier=Modifier.fillMaxWidth()){password="";vm.closeLogin()}
        Text("Sumi is an unofficial Crunchyroll client. Your password is sent directly to Crunchyroll over HTTPS and isn’t saved.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
        Action("Open Crunchyroll website",Glyph.Info,modifier=Modifier.fillMaxWidth()) {
            runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.crunchyroll.com/login")))}.onFailure {vm.reportExternalLinkFailure()}
        }
    }
}

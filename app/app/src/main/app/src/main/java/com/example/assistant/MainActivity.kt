package com.example.assistant   // doit être identique au namespace de ton projet

import android.Manifest
import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var status: TextView
    private var recognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        status = TextView(this).apply {
            text = "Appuie sur le bouton et parle"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val button = Button(this).apply {
            text = "🎤 Parler"
            textSize = 22f
            setOnClickListener { listen() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(status)
            addView(button)
        })

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onInit(s: Int) {
        if (s == TextToSpeech.SUCCESS) tts.language = Locale.FRENCH
    }

    private fun say(msg: String) {
        status.text = msg
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "a")
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Reconnaissance vocale indisponible"); return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) {
                    val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text != null) handle(text)
                }
                override fun onError(e: Int) { status.text = "Je n'ai pas compris, réessaie" }
                override fun onReadyForSpeech(p: Bundle?) { status.text = "Je t'écoute…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            })
        }
    }

    private fun handle(raw: String) {
        val t = raw.lowercase(Locale.FRENCH).trim()
        status.text = "« $raw »"
        when {
            t.startsWith("ouvre ") || t.startsWith("ouvrir ") ->
                openApp(t.substringAfter(" ").trim())
            t.contains("quelle heure") ->
                say("Il est " + SimpleDateFormat("HH 'heures' mm", Locale.FRENCH).format(Date()))
            t.contains("quel jour") || t.contains("quelle date") ->
                say("Nous sommes le " + SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH).format(Date()))
            t.startsWith("itinéraire vers ") || t.startsWith("emmène-moi à ") -> {
                val dest = t.substringAfter(" vers ", t.substringAfter(" à "))
                say("Itinéraire vers $dest")
                go(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(dest))))
            }
            t.startsWith("appelle ") -> {
                say("Ouverture du composeur")
                go(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + t.substringAfter(" ").filter { it.isDigit() })))
            }
            t.startsWith("youtube ") || t.startsWith("mets ") -> {
                val q = t.substringAfter(" ")
                say("Je cherche $q sur YouTube")
                go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
            }
            else -> {
                val q = t.removePrefix("cherche ").removePrefix("recherche ")
                say("Je cherche $q")
                val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)
                if (!go(i)) go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
            }
        }
    }

    private fun openApp(name: String) {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(launcher, 0).firstOrNull {
            it.loadLabel(pm).toString().lowercase(Locale.FRENCH).contains(name)
        }
        val intent = match?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
        if (intent != null) { say("J'ouvre $name"); startActivity(intent) }
        else say("Je ne trouve pas l'application $name")
    }

    private fun go(i: Intent): Boolean = try { startActivity(i); true } catch (e: Exception) { false }

    override fun onDestroy() {
        recognizer?.destroy(); tts.shutdown(); super.onDestroy()
    }
}

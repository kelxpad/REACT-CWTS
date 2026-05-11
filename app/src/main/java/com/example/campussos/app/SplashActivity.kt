package com.example.campussos.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import com.example.campussos.R

class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val ringOuter = findViewById<android.view.View>(R.id.splash_ring_outer)
        val ringInner = findViewById<android.view.View>(R.id.splash_ring_inner)
        val logoContainer = findViewById<android.view.View>(R.id.splash_logo_container)
        val tagline = findViewById<android.view.View>(R.id.splash_tagline)
        val bottom = findViewById<android.view.View>(R.id.splash_bottom)

        ringOuter.startAnimation(AnimationUtils.loadAnimation(this, R.anim.splash_ring_pulse))
        ringInner.startAnimation(AnimationUtils.loadAnimation(this, R.anim.splash_ring_pulse_delayed))
        logoContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.splash_logo_enter))
        tagline.startAnimation(AnimationUtils.loadAnimation(this, R.anim.splash_text_enter))
        bottom.startAnimation(AnimationUtils.loadAnimation(this, R.anim.splash_bottom_enter))

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2400)
    }
}

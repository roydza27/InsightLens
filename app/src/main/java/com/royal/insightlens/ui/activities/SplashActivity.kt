package com.royal.insightlens.ui.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.royal.insightlens.R

@Suppress("DEPRECATION")
class SplashActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var percentText: TextView
    private lateinit var statusText: TextView

    private val statusMessages = mapOf(
        0  to "Preparing insights...",
        25 to "Loading AI models...",
        50 to "Calibrating scanner...",
        75 to "Almost ready...",
        95 to "Finalizing setup..."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        progressBar = findViewById(R.id.splash_progress)
        percentText = findViewById(R.id.splash_percent)
        statusText  = findViewById(R.id.splash_status_text)

        startLoadingAnimation()
    }

    private fun startLoadingAnimation() {
        val animator = ValueAnimator.ofInt(0, 100).apply {
            duration     = 3000L
            interpolator = DecelerateInterpolator(1.5f)
            startDelay   = 400L

            addUpdateListener { anim ->
                val progress = anim.animatedValue as Int
                progressBar.progress = progress
                percentText.text = "$progress%"

                statusMessages.entries
                    .filter { progress >= it.key }
                    .maxByOrNull { it.key }
                    ?.let { statusText.text = it.value }
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    navigateToMain()
                }
            })
        }

        animator.start()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
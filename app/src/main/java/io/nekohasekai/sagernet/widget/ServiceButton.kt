package io.nekohasekai.sagernet.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.PointerIcon
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.TooltipCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.BaseProgressIndicator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class ServiceButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FloatingActionButton(context, attrs, defStyleAttr), DynamicAnimation.OnAnimationEndListener {

    private inner class IconState(@DrawableRes resId: Int) {
        val icon: Drawable =
            AppCompatResources.getDrawable(context, resId)
                ?: error("Drawable resource not found: $resId")

        fun start() {
            setImageDrawable(icon)
        }

        fun stop() = Unit
    }

    private val iconStopped by lazy { IconState(R.drawable.ic_service_stopped) }
    private val iconConnecting by lazy { IconState(R.drawable.ic_service_connecting) }
    private val iconConnected by lazy { IconState(R.drawable.ic_service_connected) }
    private val iconStopping by lazy { IconState(R.drawable.ic_service_stopping) }

    private var checked = false
    private var delayedAnimation: Job? = null
    private lateinit var progress: BaseProgressIndicator<*>

    fun initProgress(progress: BaseProgressIndicator<*>) {
        this.progress = progress
        progress.progressDrawable?.addSpringAnimationEndListener(this)
    }

    override fun onAnimationEnd(
        animation: DynamicAnimation<out DynamicAnimation<*>>?,
        canceled: Boolean,
        value: Float,
        velocity: Float
    ) {
        if (!canceled) progress.hide()
    }

    private fun hideProgress() {
        delayedAnimation?.cancel()
        if (::progress.isInitialized) {
            progress.hide()
        }
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (checked) {
            View.mergeDrawableStates(drawableState, intArrayOf(android.R.attr.state_checked))
        }
        return drawableState
    }

    fun changeState(state: BaseService.State, previousState: BaseService.State, animate: Boolean) {
        when (state) {
            BaseService.State.Connecting -> {
                hideProgress()
                if (::progress.isInitialized) {
                    delayedAnimation =
                        (context as LifecycleOwner).lifecycleScope.launchWhenStarted {
                            delay(
                                context.resources.getInteger(android.R.integer.config_mediumAnimTime) + 1000L
                            )
                            progress.isIndeterminate = true
                            progress.show()
                        }
                }
                changeState(iconConnecting, animate)
            }

            BaseService.State.Connected -> {
                delayedAnimation?.cancel()
                if (::progress.isInitialized) {
                    progress.setProgressCompat(1, true)
                }
                changeState(iconConnected, animate)
            }

            BaseService.State.Stopping -> {
                hideProgress()
                changeState(iconStopping, animate && previousState == BaseService.State.Connected)
            }

            else -> {
                hideProgress()
                changeState(iconStopped, animate)
            }
        }

        checked = state == BaseService.State.Connected
        refreshDrawableState()

        val description = context.getText(if (state.canStop) R.string.stop else R.string.connect)
        contentDescription = description
        TooltipCompat.setTooltipText(this, description)

        val enabled = state.canStop || state == BaseService.State.Stopped
        isEnabled = enabled

        if (Build.VERSION.SDK_INT >= 24) {
            pointerIcon = PointerIcon.getSystemIcon(
                context,
                if (enabled) PointerIcon.TYPE_HAND else PointerIcon.TYPE_WAIT
            )
        }
    }

    private fun changeState(icon: IconState, animate: Boolean) {
        icon.stop()
        icon.start()
    }
}
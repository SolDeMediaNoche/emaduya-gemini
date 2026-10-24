
package com.wave.music

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MusicPlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_SET_TIMER = "com.wave.music.SET_TIMER"
        const val ACTION_CANCEL_TIMER = "com.wave.music.CANCEL_TIMER"
        const val KEY_TIMER_MINUTES = "timer_minutes"
    }

    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var deadline: Long? = null

    private val timerTask = object : Runnable {
        override fun run() {
            val end = deadline ?: return
            val remaining = end - SystemClock.elapsedRealtime()

            if (remaining <= 0L) {
                mediaSession?.player?.let { player ->
                    player.pause()
                    player.seekTo(0L)
                }
                deadline = null
            } else {
                handler.postDelayed(this, minOf(remaining, 1000L))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this).build()

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {

                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val commands =
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                            .buildUpon()
                            .add(SessionCommand(ACTION_SET_TIMER, Bundle.EMPTY))
                            .add(SessionCommand(ACTION_CANCEL_TIMER, Bundle.EMPTY))
                            .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {

                    when (customCommand.customAction) {
                        ACTION_SET_TIMER -> {
                            val minutes = args.getInt(KEY_TIMER_MINUTES, 0)
                            setSleepTimer(minutes)
                        }

                        ACTION_CANCEL_TIMER -> cancelSleepTimer()
                    }

                    return Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    )
                }
            })
            .build()
    }

    private fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(timerTask)
        deadline = null

        if (minutes !in 1..180) return

        deadline = SystemClock.elapsedRealtime() + minutes * 60_000L
        handler.post(timerTask)
    }

    private fun cancelSleepTimer() {
        handler.removeCallbacks(timerTask)
        deadline = null
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        handler.removeCallbacks(timerTask)
        deadline = null

        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null

        super.onDestroy()
    }
}

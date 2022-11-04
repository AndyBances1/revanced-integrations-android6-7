package app.revanced.integrations.patches;

import java.util.Timer;

import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.utils.LogHelper;
import app.revanced.integrations.videoplayer.NewVideoInformation;

public final class FixPlaybackPatch {
    private static Thread currentThread = null;
    private static String currentVideoId;
    public static void newVideoLoaded(final String videoId) {
        if (!SettingsEnum.FIX_PLAYBACK.getBoolean()) return;

        if (videoId == null) {
            currentVideoId = null;
            return;
        }

        if (videoId.equals(currentVideoId)) return;

        currentVideoId = videoId;

        if (currentThread != null) {
            currentThread.interrupt();
        }

        currentThread = new Thread(() -> {
            while (true) {
                var currentVideoLength = PlayerControllerPatch.getCurrentVideoLength();
                long lastKnownVideoTime = NewVideoInformation.lastKnownVideoTime;
                if ((currentVideoLength > 1) || (lastKnownVideoTime > 1)) {
                    PlayerControllerPatch.seekTo(currentVideoLength);
                    PlayerControllerPatch.seekTo(1);
                    PlayerControllerPatch.seekTo(lastKnownVideoTime);
                    return;
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    LogHelper.debug(FixPlaybackPatch.class, "Thread was interrupted");
                }
            }
        });

        currentThread.start();
    }
}
package com.carassistant.voice;

/* JADX INFO: loaded from: classes.dex */
public final class PlayHandle {
    private final MediaClipPlayer mMedia;
    private final WavAudioPlayer mWav;

    private PlayHandle(WavAudioPlayer wavAudioPlayer, MediaClipPlayer mediaClipPlayer) {
        this.mWav = wavAudioPlayer;
        this.mMedia = mediaClipPlayer;
    }

    public static PlayHandle of(WavAudioPlayer wavAudioPlayer) {
        if (wavAudioPlayer == null) {
            return null;
        }
        return new PlayHandle(wavAudioPlayer, null);
    }

    public static PlayHandle of(MediaClipPlayer mediaClipPlayer) {
        if (mediaClipPlayer == null) {
            return null;
        }
        return new PlayHandle(null, mediaClipPlayer);
    }

    public void setOnEnded(Runnable runnable) {
        WavAudioPlayer wavAudioPlayer = this.mWav;
        if (wavAudioPlayer != null) {
            wavAudioPlayer.setOnEnded(runnable);
        }
        MediaClipPlayer mediaClipPlayer = this.mMedia;
        if (mediaClipPlayer != null) {
            mediaClipPlayer.setOnEnded(runnable);
        }
    }

    public boolean isPlaying() {
        WavAudioPlayer wavAudioPlayer = this.mWav;
        if (wavAudioPlayer != null) {
            return wavAudioPlayer.isPlaying();
        }
        MediaClipPlayer mediaClipPlayer = this.mMedia;
        if (mediaClipPlayer != null) {
            return mediaClipPlayer.isPlaying();
        }
        return false;
    }

    public void stop() {
        WavAudioPlayer wavAudioPlayer = this.mWav;
        if (wavAudioPlayer != null) {
            wavAudioPlayer.stop();
        }
        MediaClipPlayer mediaClipPlayer = this.mMedia;
        if (mediaClipPlayer != null) {
            mediaClipPlayer.stop();
        }
    }
}

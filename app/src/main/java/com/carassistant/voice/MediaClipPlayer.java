package com.carassistant.voice;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;

/* JADX INFO: loaded from: classes.dex */
public final class MediaClipPlayer {
    private static Throwable th;
    private static final String TAG = "MediaClipPlayer";
    private volatile Runnable mOnEnded;
    private final MediaPlayer mPlayer;
    private Thread mWatch;

    private MediaClipPlayer(MediaPlayer mediaPlayer) {
        this.mPlayer = mediaPlayer;
    }

    public void setOnEnded(Runnable runnable) {
        this.mOnEnded = runnable;
    }

    public boolean isPlaying() {
        try {
            MediaPlayer mediaPlayer = this.mPlayer;
            return mediaPlayer != null && mediaPlayer.isPlaying();
        } catch (Throwable th) {
            return false;
        }
    }

    public static MediaClipPlayer startAsset(Context context, String str, boolean z) {
        AssetFileDescriptor assetFileDescriptorOpenFd;
        if (context == null || str == null) {
            return null;
        }
        try {
            assetFileDescriptorOpenFd = context.getAssets().openFd(str);
            try {
                MediaPlayer mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioStreamType(3);
                mediaPlayer.setDataSource(assetFileDescriptorOpenFd.getFileDescriptor(), assetFileDescriptorOpenFd.getStartOffset(), assetFileDescriptorOpenFd.getLength());
                mediaPlayer.setLooping(z);
                mediaPlayer.prepare();
                mediaPlayer.start();
                MediaClipPlayer mediaClipPlayer = new MediaClipPlayer(mediaPlayer);
                if (!z) {
                    mediaClipPlayer.watchEnd();
                }
                if (assetFileDescriptorOpenFd != null) {
                    try {
                        assetFileDescriptorOpenFd.close();
                    } catch (Throwable th) {
                    }
                }
                return mediaClipPlayer;
            } catch (Throwable th2) {
                th = th2;
                try {
                    Log.e(TAG, "startAsset " + str, th);
                    return null;
                } finally {
                    if (assetFileDescriptorOpenFd != null) {
                        try {
                            assetFileDescriptorOpenFd.close();
                        } catch (Throwable th3) {
                        }
                    }
                }
            }
        } catch (Throwable th4) {
            th = th4;
            assetFileDescriptorOpenFd = null;
        }
        return null;
    }

    public static MediaClipPlayer startFile(Context context, String str, boolean z) {
        FileInputStream fileInputStream;
        if (str == null) {
            return null;
        }
        try {
            File file = new File(str);
            if (!file.isFile()) {
                return null;
            }
            fileInputStream = new FileInputStream(file);
            try {
                MediaPlayer mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioStreamType(3);
                mediaPlayer.setDataSource(fileInputStream.getFD());
                mediaPlayer.setLooping(z);
                mediaPlayer.prepare();
                mediaPlayer.start();
                MediaClipPlayer mediaClipPlayer = new MediaClipPlayer(mediaPlayer);
                if (!z) {
                    mediaClipPlayer.watchEnd();
                }
                try {
                    fileInputStream.close();
                } catch (Throwable th) {
                }
                return mediaClipPlayer;
            } catch (Throwable th2) {
                th = th2;
            }
        } catch (Throwable th3) {
            th = th3;
            fileInputStream = null;
        }
        try {
            Log.e(TAG, "startFile " + str, th);
            return null;
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (Throwable th4) {
                }
            }
        }
    }

    private void watchEnd() {
        Thread thread = new Thread(new Runnable() { // from class: com.carassistant.voice.MediaClipPlayer.1
            @Override // java.lang.Runnable
            public void run() {
                while (MediaClipPlayer.this.mPlayer != null && MediaClipPlayer.this.mPlayer.isPlaying()) {
                    try {
                        Thread.sleep(200L);
                    } catch (Throwable th) {
                    }
                }
                Runnable runnable = MediaClipPlayer.this.mOnEnded;
                MediaClipPlayer.this.mOnEnded = null;
                if (runnable != null) {
                    try {
                        runnable.run();
                    } catch (Throwable th2) {
                    }
                }
            }
        }, "MediaClipWatch");
        this.mWatch = thread;
        thread.start();
    }

    public void stop() {
        try {
            MediaPlayer mediaPlayer = this.mPlayer;
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    this.mPlayer.stop();
                }
                this.mPlayer.release();
            }
        } catch (Throwable th) {
        }
    }
}

package com.carassistant.voice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;

/* JADX INFO: loaded from: classes.dex */
public final class WavAudioPlayer {
    private static Throwable th;
    private static final String TAG = "WavAudioPlayer";
    private final boolean mLoop;
    private volatile Runnable mOnEnded;
    private final byte[] mPcm;
    private volatile boolean mPlaying;
    private volatile boolean mStop;
    private Thread mThread;
    private final AudioTrack mTrack;

    private WavAudioPlayer(AudioTrack audioTrack, byte[] bArr, boolean z) {
        this.mTrack = audioTrack;
        this.mPcm = bArr;
        this.mLoop = z;
    }

    public void setOnEnded(Runnable runnable) {
        this.mOnEnded = runnable;
    }

    public boolean isPlaying() {
        return this.mPlaying && !this.mStop;
    }

    public static WavAudioPlayer start(Context context, int i, boolean z) {
        if (context == null || i == 0) {
            return null;
        }
        try {
            return startFromBytes(readAll(context.getResources().openRawResource(i)), z);
        } catch (Throwable th) {
            Log.e(TAG, "start fail res=" + i, th);
            return null;
        }
    }

    public static WavAudioPlayer startFile(Context context, String str, boolean z) {
        FileInputStream fileInputStream;
        if (str == null || str.length() == 0) {
            return null;
        }
        try {
            fileInputStream = new FileInputStream(str);
            try {
                WavAudioPlayer wavAudioPlayerStartFromBytes = startFromBytes(readAll(fileInputStream), z);
                try {
                    fileInputStream.close();
                } catch (Throwable th) {
                }
                return wavAudioPlayerStartFromBytes;
            } catch (Throwable th2) {
                th = th2;
                try {
                    Log.e(TAG, "startFile fail " + str, th);
                    return null;
                } finally {
                    if (fileInputStream != null) {
                        try {
                            fileInputStream.close();
                        } catch (Throwable th3) {
                        }
                    }
                }
            }
        } catch (Throwable th4) {
            th = th4;
            fileInputStream = null;
        }
        return null;
    }

    private static WavAudioPlayer startFromBytes(byte[] bArr, boolean z) {
        if (bArr == null) {
            return null;
        }
        try {
            WavInfo wav = parseWav(bArr);
            if (wav == null) {
                Log.e(TAG, "not pcm wav");
                return null;
            }
            int i = wav.channels == 1 ? 4 : 12;
            int minBufferSize = AudioTrack.getMinBufferSize(wav.sampleRate, i, 2);
            if (minBufferSize <= 0) {
                minBufferSize = wav.pcm.length;
            }
            AudioTrack audioTrack = new AudioTrack(new AudioAttributes.Builder().setUsage(1).setContentType(2).build(), new AudioFormat.Builder().setSampleRate(wav.sampleRate).setEncoding(2).setChannelMask(i).build(), Math.max(minBufferSize, Math.min(wav.pcm.length, minBufferSize * 4)), 1, 0);
            if (audioTrack.getState() != 1) {
                audioTrack.release();
                Log.e(TAG, "AudioTrack init fail");
                return null;
            }
            audioTrack.setVolume(1.0f);
            WavAudioPlayer wavAudioPlayer = new WavAudioPlayer(audioTrack, wav.pcm, z);
            wavAudioPlayer.begin();
            return wavAudioPlayer;
        } catch (Throwable th) {
            Log.e(TAG, "startFromBytes fail", th);
            return null;
        }
    }

    private void begin() {
        this.mStop = false;
        this.mPlaying = true;
        this.mTrack.play();
        Thread thread = new Thread(new Runnable() { // from class: com.carassistant.voice.WavAudioPlayer.1
            @Override // java.lang.Runnable
            public void run() {
                Runnable runnable = null;
                int iWrite;
                do {
                    int i = 0;
                    while (true) {
                        try {
                            if (WavAudioPlayer.this.mStop || i >= WavAudioPlayer.this.mPcm.length || (iWrite = WavAudioPlayer.this.mTrack.write(WavAudioPlayer.this.mPcm, i, Math.min(4096, WavAudioPlayer.this.mPcm.length - i))) < 0) {
                                break;
                            }
                            i += iWrite;
                        } catch (Throwable th) {
                            try {
                                Log.w(WavAudioPlayer.TAG, "write", th);
                                if (runnable != null) {
                                    try {
                                        return;
                                    } catch (Throwable th2) {
                                        return;
                                    }
                                }
                                return;
                            } finally {
                                WavAudioPlayer.this.mPlaying = false;
                                try {
                                    WavAudioPlayer.this.mTrack.stop();
                                } catch (Throwable th3) {
                                }
                                runnable = WavAudioPlayer.this.mOnEnded;
                                WavAudioPlayer.this.mOnEnded = null;
                                if (runnable != null) {
                                    try {
                                        runnable.run();
                                    } catch (Throwable th4) {
                                    }
                                }
                            }
                        }
                    }
                    if (!WavAudioPlayer.this.mLoop) {
                        break;
                    }
                } while (!WavAudioPlayer.this.mStop);
                WavAudioPlayer.this.mPlaying = false;
                try {
                    WavAudioPlayer.this.mTrack.stop();
                } catch (Throwable th5) {
                }
                Runnable runnable2 = WavAudioPlayer.this.mOnEnded;
                WavAudioPlayer.this.mOnEnded = null;
                if (runnable2 != null) {
                    try {
                        runnable2.run();
                    } catch (Throwable th6) {
                    }
                }
            }
        }, TAG);
        this.mThread = thread;
        thread.start();
    }

    public void stop() {
        this.mStop = true;
        try {
            AudioTrack audioTrack = this.mTrack;
            if (audioTrack != null) {
                audioTrack.pause();
                this.mTrack.flush();
                this.mTrack.stop();
                this.mTrack.release();
            }
        } catch (Throwable th) {
        }
        Thread thread = this.mThread;
        if (thread != null) {
            try {
                thread.join(300L);
            } catch (Throwable th2) {
            }
            this.mThread = null;
        }
    }

    private static byte[] readAll(InputStream inputStream) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bArr = new byte[8192];
        while (true) {
            int i = inputStream.read(bArr);
            if (i >= 0) {
                byteArrayOutputStream.write(bArr, 0, i);
            } else {
                inputStream.close();
                return byteArrayOutputStream.toByteArray();
            }
        }
    }

    private static WavInfo parseWav(byte[] bArr) {
        int i;
        int iU32;
        if (bArr == null || bArr.length < 44 || bArr[0] != 82 || bArr[1] != 73 || bArr[2] != 70 || bArr[3] != 70 || bArr[8] != 87 || bArr[9] != 65 || bArr[10] != 86 || bArr[11] != 69) {
            return null;
        }
        int i2 = 12;
        int i3 = 22050;
        int iU16 = 1;
        while (true) {
            i = i2 + 8;
            if (i > bArr.length) {
                i = -1;
                iU32 = 0;
                break;
            }
            int i4 = bArr[i2] & 255;
            int i5 = bArr[i2 + 1] & 255;
            int i6 = bArr[i2 + 2] & 255;
            int i7 = bArr[i2 + 3] & 255;
            iU32 = u32(bArr, i2 + 4);
            if (i4 == 102 && i5 == 109 && i6 == 116 && i7 == 32) {
                int iU17 = u16(bArr, i);
                iU16 = u16(bArr, i + 2);
                int iU33 = u32(bArr, i + 4);
                int iU18 = u16(bArr, i + 14);
                if (iU17 != 1 || iU18 != 16) {
                    Log.e(TAG, "need pcm16 got fmt=" + iU17 + " bits=" + iU18);
                    return null;
                }
                i3 = iU33;
            } else if (i4 == 100 && i5 == 97 && i6 == 116 && i7 == 97) {
                break;
            }
            i2 = iU32 + (iU32 & 1) + i;
        }
        if (i < 0 || i + iU32 > bArr.length) {
            return null;
        }
        byte[] bArr2 = new byte[iU32];
        System.arraycopy(bArr, i, bArr2, 0, iU32);
        WavInfo wavInfo = new WavInfo();
        wavInfo.sampleRate = i3;
        wavInfo.channels = iU16;
        wavInfo.pcm = bArr2;
        return wavInfo;
    }

    private static int u16(byte[] bArr, int i) {
        return ((bArr[i + 1] & 255) << 8) | (bArr[i] & 255);
    }

    private static int u32(byte[] bArr, int i) {
        return ((bArr[i + 3] & 255) << 24) | (bArr[i] & 255) | ((bArr[i + 1] & 255) << 8) | ((bArr[i + 2] & 255) << 16);
    }

    private static final class WavInfo {
        int channels;
        byte[] pcm;
        int sampleRate;

        private WavInfo() {
        }
    }
}

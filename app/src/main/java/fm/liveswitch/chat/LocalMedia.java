package fm.liveswitch.chat;

import android.content.Context;

import fm.liveswitch.*;
import fm.liveswitch.android.*;

public abstract class LocalMedia<TView> extends fm.liveswitch.RtcLocalMedia<TView> {

    private boolean enableSoftwareH264;
    protected Context context;


    @Override
    protected AudioSink createAudioRecorder(AudioFormat audioFormat) {
        return new fm.liveswitch.matroska.AudioSink(getId() + "-local-audio-" + audioFormat.getName().toLowerCase() + ".mkv");
    }

    @Override
    protected VideoSink createVideoRecorder(VideoFormat videoFormat) {
        return new fm.liveswitch.matroska.VideoSink(getId() + "-local-video-" + videoFormat.getName().toLowerCase() + ".mkv");
    }

    @Override
    protected VideoPipe createImageConverter(VideoFormat videoFormat) {
        return new fm.liveswitch.yuv.ImageConverter(videoFormat);
    }

    @Override
    protected AudioSource createAudioSource(AudioConfig audioConfig) {
        return new AudioRecordSource(context, audioConfig);
    }

    @Override
    protected AudioEncoder createOpusEncoder(AudioConfig audioConfig) {
        return new fm.liveswitch.opus.Encoder(audioConfig);
    }

    @Override
    protected VideoEncoder createH264Encoder() {
        if (enableSoftwareH264) {
            return new fm.liveswitch.openh264.Encoder();
        } else {
            return null;
        }
    }

    @Override
    protected VideoEncoder createVp8Encoder() {
        return new fm.liveswitch.vp8.Encoder();
    }

    @Override
    protected VideoEncoder createVp9Encoder() {
        return new fm.liveswitch.vp9.Encoder();
    }

    public LocalMedia(Context context, boolean enableSoftwareH264, boolean disableAudio, boolean disableVideo, AecContext aecContext) {
        super(disableAudio, disableVideo, aecContext);
        this.enableSoftwareH264 = enableSoftwareH264;
        this.context = context;
    }
}

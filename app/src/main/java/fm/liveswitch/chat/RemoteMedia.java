package fm.liveswitch.chat;

import android.content.Context;
import android.widget.FrameLayout;
import android.widget.ImageView;

import fm.liveswitch.*;
import fm.liveswitch.android.*;

public class RemoteMedia extends RtcRemoteMedia<ImageView> {

    private boolean enableSoftwareH264;
    private Context context;

    @Override
    protected ViewSink<ImageView> createViewSink() {
        return new ImageViewSink(context);
    }

    @Override
    protected AudioSink createAudioRecorder(AudioFormat audioFormat) {
        return new fm.liveswitch.matroska.AudioSink(getId() + "-remote-audio-" + audioFormat.getName().toLowerCase() + ".mkv");
    }

    @Override
    protected VideoSink createVideoRecorder(VideoFormat videoFormat) {
        return new fm.liveswitch.matroska.VideoSink(getId() + "-remote-video-" + videoFormat.getName().toLowerCase() + ".mkv");
    }

    @Override
    protected VideoPipe createImageConverter(VideoFormat videoFormat) {
        return new fm.liveswitch.yuv.ImageConverter(videoFormat);
    }

    @Override
    protected AudioDecoder createOpusDecoder(AudioConfig audioConfig) {
        return new fm.liveswitch.opus.Decoder(audioConfig);
    }

    @Override
    protected AudioSink createAudioSink(AudioConfig audioConfig) {
        return new fm.liveswitch.android.AudioTrackSink(audioConfig);
    }

    @Override
    protected VideoDecoder createH264Decoder() {
        if (enableSoftwareH264) {
            return new fm.liveswitch.openh264.Decoder();
        } else {
            return null;
        }
    }

    @Override
    protected VideoDecoder createVp8Decoder() {
        return new fm.liveswitch.vp8.Decoder();
    }

    @Override
    protected VideoDecoder createVp9Decoder() {
        return new fm.liveswitch.vp9.Decoder();
    }

    public RemoteMedia(final Context context, boolean enableSoftwareH264, boolean disableAudio, boolean disableVideo, AecContext aecContext) {
        super(disableAudio, disableVideo, aecContext);
        this.context = context;
        this.enableSoftwareH264 = enableSoftwareH264;

        super.initialize();
    }
}

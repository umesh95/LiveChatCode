package fm.liveswitch.chat;

import android.content.Context;
import android.media.projection.MediaProjection;
import android.widget.FrameLayout;

import fm.liveswitch.VideoEncodingConfig;
import fm.liveswitch.VideoSource;
import fm.liveswitch.ViewSink;
import fm.liveswitch.android.MediaProjectionSource;

public class ScreenShareLocalMedia extends LocalMedia<FrameLayout> {

    private MediaProjectionSource projectionSource;

    @Override
    protected ViewSink<FrameLayout> createViewSink() {
        return new fm.liveswitch.android.OpenGLSink(context);
    }

    @Override
    protected VideoSource createVideoSource() {
        return projectionSource;
    }

    public ScreenShareLocalMedia(MediaProjection projection, Context context, boolean enableSoftwareH264, boolean disableAudio, boolean disableVideo, AecContext aecContext, boolean enableSimulcast) {
        super(context, enableSoftwareH264, disableAudio, disableVideo, aecContext);
        this.context = context;
        projectionSource = new MediaProjectionSource(projection, context, 1);

        if (enableSimulcast) {
            VideoEncodingConfig[] videoEncodings = new VideoEncodingConfig[3];
            videoEncodings[0] = new VideoEncodingConfig();
            videoEncodings[1] = new VideoEncodingConfig();
            videoEncodings[2] = new VideoEncodingConfig();

            videoEncodings[0].setBitrate(1024);
            videoEncodings[0].setFrameRate(30);

            videoEncodings[1].setBitrate(512);
            videoEncodings[1].setFrameRate(15);
            videoEncodings[1].setScale(0.5);

            videoEncodings[2].setDeactivated(true);
            videoEncodings[2].setBitrate(256);
            videoEncodings[2].setFrameRate(7.5);
            videoEncodings[2].setScale(0.25);

            this.setVideoEncodings(videoEncodings);
        }

        super.initialize();
    }
}
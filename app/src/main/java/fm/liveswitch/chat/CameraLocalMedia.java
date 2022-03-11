package fm.liveswitch.chat;

import android.content.Context;
import android.view.View;

import fm.liveswitch.LayoutScale;
import fm.liveswitch.VideoConfig;
import fm.liveswitch.VideoEncodingConfig;
import fm.liveswitch.VideoSource;
import fm.liveswitch.ViewSink;
import fm.liveswitch.android.Camera2Source;
import fm.liveswitch.android.CameraPreview;

public class CameraLocalMedia extends LocalMedia<View> {
    private CameraPreview viewSink;
    private VideoConfig videoConfig = new VideoConfig(640, 480, 30);

    @Override
    protected ViewSink<View> createViewSink() {
        return null;
    }

    @Override
    protected VideoSource createVideoSource() {
        return new Camera2Source(viewSink, videoConfig);
    }


    public CameraLocalMedia(Context context, boolean enableSoftwareH264, boolean disableAudio, boolean disableVideo, AecContext aecContext, boolean enableSimulcast) {
        super(context, enableSoftwareH264, disableAudio, disableVideo, aecContext);
        this.context = context;

        viewSink = new CameraPreview(context, LayoutScale.Contain);

        this.setVideoSimulcastDisabled(!enableSimulcast);

        super.initialize();
    }

    public View getView()
    {
        return viewSink.getView();
    }
}
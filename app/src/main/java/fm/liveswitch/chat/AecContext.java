package fm.liveswitch.chat;

import fm.liveswitch.AecPipe;
import fm.liveswitch.AudioConfig;
import fm.liveswitch.AudioSink;
import fm.liveswitch.android.AudioRecordSource;
import fm.liveswitch.android.AudioTrackSink;
import fm.liveswitch.audioprocessing.AecProcessor;

public class AecContext extends fm.liveswitch.AecContext {
    @Override
    protected AecPipe createProcessor() {
        AudioConfig config = new AudioConfig(48000, 2);
        return new AecProcessor(config, AudioTrackSink.getBufferDelay(config) + AudioRecordSource.getBufferDelay(config));
    }

    @Override
    protected AudioSink createOutputMixerSink(AudioConfig audioConfig) {
        return new AudioTrackSink(audioConfig);
    }
}

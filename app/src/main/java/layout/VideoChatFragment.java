package layout;

import android.support.v4.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.view.ContextMenu;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import fm.liveswitch.chat.App;
import fm.liveswitch.chat.R;
import fm.liveswitch.*;

public class VideoChatFragment extends Fragment implements View.OnTouchListener {
    private App app;
    public static RelativeLayout container;
    private FrameLayout layout;

    private String current_id;

    private GestureDetector gestureDetector;

    private OnVideoReadyListener listener;

    private ArrayList<Integer> sendEncodings;
    private ArrayList<Integer> recvEncodings;
    private String prefix = "Bitrate: ";


    public VideoChatFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = App.getInstance(null);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState)
    {
        try {
            if (getActivity() != null) {
                // For demonstration purposes, use the double-tap gesture
                // to switch between the front and rear camera.
                gestureDetector = new GestureDetector(this.getActivity(), new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (!app.getIsScreenShareEnabled())
                        {
                            app.useNextVideoDevice();
                        }
                        return true;
                    }
                    @Override
                    public  boolean onDown(MotionEvent e) { return true; }

                });

                layout = (FrameLayout) view.findViewById(R.id.layout);
                app.videoChatFragmentLayout = this;

                getView().setOnTouchListener(this);

                // Preserve a static container across
                // activity destruction/recreation.
                RelativeLayout c = (RelativeLayout) view.findViewById(R.id.container);
                if (container == null) {
                    container = c;

                    Toast.makeText(getActivity(), "Double-tap to switch camera.", Toast.LENGTH_SHORT).show();
                }
                layout.removeView(c);

                listener.onVideoReady();

            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void onPause() {
        // Android requires us to pause the local
        // video feed when pausing the activity.
        // Not doing this can cause unexpected side
        // effects and crashes.
        app.pauseLocalVideo().waitForResult();

        // Remove the static container from the current layout.
        if (container != null) {
            layout.removeView(container);
        }

        super.onPause();
    }

    public boolean onTouch(View view, MotionEvent motionEvent) {
        // Handle the double-tap event.
        boolean gestureResponse = false;
        if (gestureDetector != null) {
            gestureResponse = gestureDetector.onTouchEvent(motionEvent);
        }
        if (!gestureResponse) {
            return view.onTouchEvent(motionEvent);
        }
        return gestureResponse;
    }

    public void onResume() {
        super.onResume();

        // Add the static container to the current layout.
        if (container != null) {
            layout.addView(container);
        }

        // Resume the local video feed.
        app.resumeLocalVideo().waitForResult();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_video_chat, container, false);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        String id = v.getContentDescription().toString();
        current_id = id;
        int index = 0;
        if (id.equals("localView")) {
            menu.setHeaderTitle("Local");
            MenuItem muteAudio = menu.add(0, 0, 0, "Mute Audio");
            MenuItem muteVideo = menu.add(0, 1, 0, "Mute Video");
            MenuItem disableAudio = menu.add(0, 2, 0, "Disable Audio");
            MenuItem disableVideo = menu.add(0, 3, 0, "Disable Video");
            menu.setGroupCheckable(0, true, false);
            muteAudio.setChecked(app.contextMenuItemFlag.get("MuteAudio"));
            muteVideo.setChecked(app.contextMenuItemFlag.get("MuteVideo"));
            disableAudio.setChecked(app.contextMenuItemFlag.get("DisableAudio"));
            disableVideo.setChecked(app.contextMenuItemFlag.get("DisableVideo"));
            if (app.getEnableSimulcast()) {
                if (sendEncodings != null) {
                    Collections.sort(sendEncodings);
                    Collections.reverse(sendEncodings);
                    SubMenu encodings = menu.addSubMenu(0, 2, 0, "Video Encoding");
                    for (int bitrate : sendEncodings) {
                        MenuItem item = encodings.add(1, index, 0, prefix + bitrate);
                        item.setChecked(app.contextMenuItemFlag.get(id + prefix + bitrate));
                        index++;
                    }
                    encodings.setGroupCheckable(1, true, false);
                }
            }
        } else {
            menu.setHeaderTitle("Remote");
            MenuItem disableAudio = menu.add(2, 0, 0, "Disable Audio");
            MenuItem disableVideo = menu.add(2, 1, 0, "Disable Video");
            menu.setGroupCheckable(2, true, false);
            disableAudio.setChecked(app.contextMenuItemFlag.get(id + "DisableAudio"));
            disableVideo.setChecked(app.contextMenuItemFlag.get(id + "DisableVideo"));
            if (app.getEnableSimulcast()) {
                // Refresh the recvEncoding List in case of each remote media has different encoding
                if (recvEncodings != null){
                    recvEncodings.clear();
                    for (Map.Entry flag : app.contextMenuItemFlag.entrySet()) {
                        String key = (String)flag.getKey();
                        if (key.contains(id + prefix)) {
                            recvEncodings.add(Integer.parseInt(key.split(":")[1].trim()));
                        }
                    }
                    Collections.sort(recvEncodings);
                    Collections.reverse(recvEncodings);
                    SubMenu encodings = menu.addSubMenu(2, 2, 0, "Video Encoding");
                    for (int bitrate : recvEncodings) {
                        MenuItem item = encodings.add(3, index, 0, prefix + bitrate);
                        item.setChecked(app.contextMenuItemFlag.get(id + prefix + bitrate));
                        index++;
                    }
                    encodings.setGroupCheckable(3, true, false);
                }
            }
        }

    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        String id = current_id;
        int itemId = item.getItemId();
        if (item.getGroupId() == 0) {
            switch (item.getItemId()) {
                case 0:
                    app.toggleMuteAudio();
                    break;
                case 1:
                    app.toggleMuteVideo();
                    break;
                case 2:
                    app.toggleLocalDisableAudio();
                    break;
                case 3:
                    app.toggleLocalDisableVideo();
                    break;
            }
        }
        if (item.getGroupId() == 2) {
            switch (item.getItemId()) {
                case 0:
                    app.toggleRemoteDisableAudio(id);
                    break;
                case 1:
                    app.toggleRemoteDisableVideo(id);
                    break;
            }
        }
        if (item.getGroupId() == 1) {
            //toggleSendEncoding on local media
            app.changeSendEncodings(itemId);
            app.contextMenuItemFlag.put(id + prefix + sendEncodings.get(itemId), !app.contextMenuItemFlag.get(id + prefix + sendEncodings.get(itemId)));
        }
        if (item.getGroupId() == 3) {
            //toggleRecvEncoding on selected remote media
            app.changeReceiveEncodings(id, itemId);
            updateRecvEncodingFlag(id, recvEncodings.get(itemId));
        }
        return true;
    }

    public void updateRecvEncodingFlag(String id, int bitrate) {
        for (Map.Entry flag : app.contextMenuItemFlag.entrySet()) {
            String key = (String)flag.getKey();
            if (key.contains(id + prefix)) {
                if (key.equals(id + prefix + bitrate)) {
                    app.contextMenuItemFlag.put(key, true);
                } else {
                    app.contextMenuItemFlag.put(key, false);
                }
            }
        }
    }

    public interface OnVideoReadyListener {
        void onVideoReady();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnVideoReadyListener) {
            listener = (OnVideoReadyListener) context;
        } else {
            throw new ClassCastException(context.toString()
                    + " must implement VideoChatFragment.OnVideoReadyListener");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        app.videoChatFragmentLayout = null;
    }

    public void registerLocalContextMenu(View view, VideoEncodingConfig[] encodings) {
        String id = view.getContentDescription().toString();
        sendEncodings = new ArrayList<>();
        app.contextMenuItemFlag.put("MuteAudio", false);
        app.contextMenuItemFlag.put("MuteVideo", false);
        app.contextMenuItemFlag.put("DisableAudio", false);
        app.contextMenuItemFlag.put("DisableVideo", false);
        if (encodings != null && encodings.length > 1) {
            for (int i = 0; i < encodings.length; i++) {
                int bitrate = getBitrate(encodings[i].toString());
                sendEncodings.add(bitrate);
                app.contextMenuItemFlag.put(id + prefix + bitrate, true);
            }
        }
        registerForContextMenu(view);
    }

    public void registerRemoteContextMenu(View view, EncodingInfo[] encodings) {
        String id = view.getContentDescription().toString();
        recvEncodings = new ArrayList<>();
        app.contextMenuItemFlag.put(id + "DisableAudio", false);
        app.contextMenuItemFlag.put(id + "DisableVideo", false);
        if (encodings != null && encodings.length > 1) {
            for (int i = 0; i < encodings.length; i++) {
                int bitrate = getBitrate(encodings[i].toString());
                recvEncodings.add(bitrate);
                app.contextMenuItemFlag.put(id + prefix + bitrate, i == 0);
            }
        }
        registerForContextMenu(view);
    }

    private int getBitrate(String encoding) {
        String[] str = encoding.split(",");
        for (int i = 0; i < str.length; i++) {
            if (str[i].contains("Bitrate")) {
                return Integer.parseInt(str[i].split(":")[1].trim());
            }
        }
        return 0;
    }
}
